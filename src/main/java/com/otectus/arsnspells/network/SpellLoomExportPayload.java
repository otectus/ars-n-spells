package com.otectus.arsnspells.network;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.block.SpellLoomBlockEntity;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.contract.InscriptionPlan;
import com.otectus.arsnspells.menu.SpellLoomMenu;
import com.otectus.arsnspells.rituals.LoomInscriptionView;
import com.otectus.arsnspells.spell.ArsSpellExportUtil;
import com.otectus.arsnspells.spell.CrossModSpellComponents;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import com.otectus.arsnspells.util.AdvancementUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;

/**
 * Client-to-server request from the Spell Loom screen to inscribe the source slot's
 * Ars spell — with the chosen display name, nature and icon — onto the blank Iron's
 * scroll in the scroll slot, placing the carrier in the output slot.
 *
 * <p>Server-authoritative: the client only sends the cosmetic choices. The server
 * re-reads the block entity's slots (never trusting client item state), validates,
 * and performs the mutation. Play payload handlers run on the main thread, so no
 * explicit enqueue is needed.
 *
 * <p>{@code reasonCode} is the {@link InscriptionPlan} reason the sending screen's own preview
 * produced (audit V18). It is never trusted - the server re-plans from its own slots - but a
 * request that disagrees with the server's plan is refused rather than run, so a client working
 * from a stale preview cannot act on a rule the server no longer applies. Adding it changed the
 * wire shape, hence the {@code PacketHandler.PROTOCOL_VERSION} bump that came with it.
 */
public record SpellLoomExportPayload(String name, String nature, String iconSymbol,
                                     String reasonCode)
    implements CustomPacketPayload {

    public static final int MAX_NAME = 40;

    /**
     * Wire cap on every string field (ANS-MED-016). This is a C2S payload, so an unbounded
     * read lets any client make the server allocate 32 KB per field before validation runs.
     * Generous versus MAX_NAME because the nature/icon fields are whitelisted by value, not
     * truncated, and a slightly-too-long value should be rejected rather than silently cut.
     */
    private static final int MAX_WIRE_STRING = 128;

    public static final Type<SpellLoomExportPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArsNSpells.MODID, "spell_loom_export"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpellLoomExportPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.stringUtf8(MAX_WIRE_STRING), SpellLoomExportPayload::name,
            ByteBufCodecs.stringUtf8(MAX_WIRE_STRING), SpellLoomExportPayload::nature,
            ByteBufCodecs.stringUtf8(MAX_WIRE_STRING), SpellLoomExportPayload::iconSymbol,
            ByteBufCodecs.stringUtf8(MAX_WIRE_STRING), SpellLoomExportPayload::reasonCode,
            SpellLoomExportPayload::new
        );

    /**
     * The request the screen sends: the cosmetic choices plus the reason code its own preview
     * arrived at, which is {@link InscriptionPlan#REASON_OK} whenever the button was clickable.
     */
    public static SpellLoomExportPayload request(String name, String nature, String iconSymbol,
                                                 String reasonCode) {
        return new SpellLoomExportPayload(name, nature, iconSymbol,
            reasonCode == null ? "" : reasonCode);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(SpellLoomExportPayload payload, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sender)
            || !(sender.containerMenu instanceof SpellLoomMenu menu)) {
            return;
        }
        SpellLoomBlockEntity be = menu.getBlockEntity();
        if (be == null || !menu.stillValid(sender)) {
            return;
        }
        ItemStackHandler items = be.getItems();
        ItemStack source = items.getStackInSlot(SpellLoomBlockEntity.SLOT_SOURCE);
        ItemStack scroll = items.getStackInSlot(SpellLoomBlockEntity.SLOT_SCROLL);
        ItemStack output = items.getStackInSlot(SpellLoomBlockEntity.SLOT_OUTPUT);

        if (!output.isEmpty()) {
            return; // output occupied; player must clear it first
        }
        if (!IronsCompat.isLoaded()) {
            sender.displayClientMessage(
                Component.translatable("ars_n_spells.spell_loom.error.irons_missing"), true);
            return;
        }
        // The real Iron's scroll item. Whether it is *blank* is not asked here - that is the
        // planner's call below, and asking it twice is how the two answers drifted apart.
        if (!IronsBookBindingUtil.isIronsScroll(scroll)) {
            sender.displayClientMessage(
                Component.translatable("ars_n_spells.spell_loom.error.no_scroll"), true);
            return;
        }
        // Audit V18: the loom used to read "blank" as "carries no ANS data" and consume a unit
        // of the source unconditionally, so it overwrote filled scrolls and ate spellbooks. One
        // plan now answers both questions, before anything moves.
        InscriptionPlan plan = LoomInscriptionView.plan(source, scroll);
        if (!plan.isPermitted()) {
            // The reason code travels to the player as the rejection text, so the screen can
            // state which rule fired instead of "could not inscribe".
            sender.displayClientMessage(
                Component.translatable("ars_n_spells.spell_loom.error.rejected",
                    plan.reasonCode()), true);
            return;
        }
        // The client previews with the same planner. A request whose stated reason disagrees
        // with the server's is a client acting on a stale or edited preview; it is refused
        // rather than run, because the two sides no longer agree on what is about to happen.
        if (!InscriptionPlan.REASON_OK.equals(payload.reasonCode())) {
            sender.displayClientMessage(
                Component.translatable("ars_n_spells.spell_loom.error.rejected",
                    payload.reasonCode()), true);
            return;
        }
        Optional<Spell> spell = ArsSpellExportUtil.extractArsSpell(source);
        if (spell.isEmpty()) {
            sender.displayClientMessage(
                Component.translatable("ars_n_spells.spell_loom.error.no_source"), true);
            return;
        }
        // Caster-bound addon glyphs (ModTags.CROSS_CAST_BLACKLIST) are refused at export, where
        // the player can still fix the spell, rather than at cast time on a finished scroll.
        java.util.List<String> blacklisted =
            com.otectus.arsnspells.util.ArsSpellIntegrity.blacklistedGlyphIds(spell.get());
        if (!blacklisted.isEmpty()) {
            sender.displayClientMessage(
                Component.translatable("ars_n_spells.spell_loom.error.blacklisted_glyphs",
                    com.otectus.arsnspells.util.ArsSpellIntegrity.describeMissing(blacklisted)), true);
            return;
        }

        String cleanName = payload.name() == null ? "" : payload.name().trim();
        if (cleanName.length() > MAX_NAME) {
            cleanName = cleanName.substring(0, MAX_NAME);
        }
        // Whitelist the cosmetic keys: anything outside the shipped sets is
        // dropped (empty = "use defaults"), so a hand-crafted packet cannot
        // stamp data that later resolves to a missing wheel texture.
        String cleanNature = CrossModSpellComponents.NATURE_KEYS.contains(payload.nature())
            ? payload.nature() : "";
        String cleanIcon = CrossModSpellComponents.ICON_SYMBOLS.contains(payload.iconSymbol())
            ? payload.iconSymbol() : "";
        ItemStack carrier = ArsSpellExportUtil.createIronsScrollCarrier(
            spell.get(), cleanName, cleanNature, cleanIcon);
        if (carrier.isEmpty()) {
            sender.displayClientMessage(
                Component.translatable("ars_n_spells.spell_loom.error.failed"), true);
            return;
        }

        // One atomic output: the plan says what the slots owe, the block entity moves them.
        if (!SpellLoomBlockEntity.applyInscription(items, plan, carrier)) {
            sender.displayClientMessage(
                Component.translatable("ars_n_spells.spell_loom.error.failed"), true);
            return;
        }
        be.setChanged();
        // Audit H4: transcribing has no vanilla trigger, so the advancement is code-awarded.
        // Without this the loom chain's second step was unobtainable.
        AdvancementUtil.grant(sender, "transcribe_spell");
    }
}
