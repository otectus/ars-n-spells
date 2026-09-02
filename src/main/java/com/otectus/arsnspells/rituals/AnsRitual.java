package com.otectus.arsnspells.rituals;

import com.hollingsworth.arsnouveau.api.ritual.AbstractRitual;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Base class for this mod's one-shot rituals: the ones that validate whatever is lying around the
 * brazier, do their work once, and stop.
 *
 * <p><b>Why this class exists.</b> Ars Nouveau never ends a ritual for you.
 * {@code RitualBrazierTile.tick()} holds the only call site of {@link #onEnd()} and it is guarded on
 * {@code RitualContext.isDone}; the only thing in the whole of Ars that writes {@code isDone} is
 * {@link #setFinished()}. So a ritual whose {@code tick()} does nothing lights the brazier and burns
 * forever without ever reaching {@code onEnd()}. Every one of Ars's own timed rituals calls
 * {@code setFinished()} from its own {@code tick()}; ANS's four one-shot rituals did not, which is
 * why Spellbook Binding, Spell Transcription, Spell Uninscription and Mana Infusion all silently did
 * nothing from 3.0.0 through 3.2.2. Subclasses must therefore NOT override {@link #tick()}; override
 * {@link #durationTicks()} if a ritual needs longer than the default.
 *
 * <p><b>Initiator tracking.</b> Ars hands the player who lit the brazier to
 * {@link #onStart(Player)} and then throws them away — neither {@code RitualContext} nor
 * {@code RitualBrazierTile} keeps an owner. Because the ritual burns for a few seconds before
 * {@code onEnd()} runs, feedback that searched for a nearby player found nobody whenever the player
 * walked off, and every error message was silently dropped. We keep the initiator's UUID instead,
 * persisted through {@link #write(CompoundTag)} / {@link #read(CompoundTag)} so it survives a chunk
 * unload or a restart mid-ritual. A UUID and not a {@code Player}: a retained {@code ServerPlayer}
 * outlives its own connection.
 *
 * <p><b>Subclasses must keep a public no-arg constructor.</b> {@code RitualRegistry.getRitual}
 * builds each ritual reflectively via {@code getDeclaredConstructor()}, and a missing no-arg
 * constructor surfaces only as a {@code null} ritual at runtime — nearly silent.
 */
public abstract class AnsRitual extends AbstractRitual {
    /** How long a one-shot ANS ritual burns before {@link #onEnd()} fires — 60 ticks, ~3 seconds. */
    public static final int DEFAULT_DURATION_TICKS = 60;

    private static final String TAG_INITIATOR = "ars_n_spells:initiator";

    @Nullable
    private UUID initiator;

    /** Ticks this ritual burns before finishing. Override to lengthen a particular ritual. */
    protected int durationTicks() {
        return DEFAULT_DURATION_TICKS;
    }

    @Override
    protected final void tick() {
        // Server-authoritative, matching Ars's own rituals: the client's copy of the ritual is
        // cleared when the brazier sends its block update after onEnd().
        if (!(getWorld() instanceof ServerLevel)) {
            return;
        }
        incrementProgress();
        if (getProgress() >= durationTicks()) {
            setFinished();
        }
    }

    @Override
    public void onStart(@Nullable Player player) {
        super.onStart(player);
        // Null on a redstone-triggered start (RitualBrazierBlock.neighborChanged passes null),
        // in which case feedback falls back to proximity.
        if (player != null) {
            this.initiator = player.getUUID();
        }
    }

    @Override
    public void write(CompoundTag tag) {
        super.write(tag);
        // Doubles as the brazier's client update tag, so this UUID is visible to everyone tracking
        // the block. Harmless — it is only ever used to pick a chat recipient server-side.
        if (this.initiator != null) {
            tag.putUUID(TAG_INITIATOR, this.initiator);
        }
    }

    @Override
    public void read(CompoundTag tag) {
        super.read(tag);
        // Absent on braziers saved before this key existed, and on redstone starts.
        this.initiator = tag.hasUUID(TAG_INITIATOR) ? tag.getUUID(TAG_INITIATOR) : null;
    }

    /** UUID of the player who lit the brazier, or null if it was started without one. */
    @Nullable
    protected UUID initiatorId() {
        return this.initiator;
    }

    /**
     * The player this ritual should report to: the one who lit it if they are still online,
     * otherwise the nearest player to the brazier. Used for both chat feedback and advancements so
     * the two can never disagree about who ran the ritual.
     */
    @Nullable
    protected Player recipient() {
        return RitualFeedback.resolveRecipient(getWorld(), getPos(), this.initiator);
    }

    /** Sends a red, lang-keyed failure message to {@link #recipient()}. */
    protected void error(String key, Object... args) {
        RitualFeedback.error(getWorld(), getPos(), this.initiator, key, args);
    }

    /** Sends a green, lang-keyed success message to {@link #recipient()}. */
    protected void success(String key, Object... args) {
        RitualFeedback.success(getWorld(), getPos(), this.initiator, key, args);
    }
}
