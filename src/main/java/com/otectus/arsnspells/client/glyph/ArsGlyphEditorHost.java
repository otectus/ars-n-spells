package com.otectus.arsnspells.client.glyph;

import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import java.util.Collection;
import java.util.List;

/** Implemented by an editor which can validate and insert an exact part in its own unsaved draft. */
public interface ArsGlyphEditorHost {
    BrowserController browserController();
    Collection<AbstractSpellPart> browserKnowledge();
    int browserBookTier();
    boolean browserCreative();
    boolean browserOwnsBook();
    @Nullable Component browserError(AbstractSpellPart part);
    void browserInsert(AbstractSpellPart part);
    void browserRefreshPalette();
    List<?> browserDraft();
    String browserQuery();
}
