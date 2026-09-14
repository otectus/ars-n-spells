package com.otectus.arsnspells.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Spell Loom workstation: right-click opens a menu where a player names an
 * authored Ars spell, picks a nature, designs a rudimentary icon, and inscribes
 * it onto a blank Iron's scroll.
 */
public class SpellLoomBlock extends Block implements EntityBlock {
    // The tabletop and four feet/posts follow the model's open silhouette. Fine
    // trim and thread geometry share the frame's envelope to keep raycasts cheap.
    private static final VoxelShape SHAPE = Shapes.or(
        Block.box(1, 9, 2, 15, 11, 14),
        Block.box(2, 9, 1, 14, 11, 15),
        Block.box(1.25, 0, 1.25, 4.25, 9, 4.25),
        Block.box(11.75, 0, 1.25, 14.75, 9, 4.25),
        Block.box(1.25, 0, 11.75, 4.25, 9, 14.75),
        Block.box(11.75, 0, 11.75, 14.75, 9, 14.75),
        Block.box(3.75, 2.5, 2.35, 12.25, 3.25, 3.15),
        Block.box(3.75, 2.5, 12.85, 12.25, 3.25, 13.65),
        Block.box(2.35, 2.5, 3.75, 3.15, 3.25, 12.25),
        Block.box(12.85, 2.5, 3.75, 13.65, 3.25, 12.25),
        Block.box(3.75, 2.5, 7.5, 12.25, 3.25, 8.5),
        Block.box(7.5, 2.5, 3.75, 8.5, 3.25, 12.25),
        Block.box(4.2, 7.25, 1.2, 11.8, 9, 1.5),
        Block.box(4.2, 7.25, 14.7, 11.8, 9, 15),
        Block.box(1.2, 7.25, 4.2, 1.5, 9, 11.8),
        Block.box(14.7, 7.25, 4.2, 15, 9, 11.8),
        Block.box(6.75, 6.5, 1.1, 9.25, 9, 1.6),
        Block.box(6.75, 6.5, 14.6, 9.25, 9, 15.1),
        Block.box(1.1, 6.5, 6.75, 1.6, 9, 9.25),
        Block.box(14.6, 6.5, 6.75, 15.1, 9, 9.25),
        Block.box(1.85, 11, 1.85, 3.65, 16, 3.65),
        Block.box(12.35, 11, 1.85, 14.15, 16, 3.65),
        Block.box(1.85, 11, 12.35, 3.65, 16, 14.15),
        Block.box(12.35, 11, 12.35, 14.15, 16, 14.15),
        Block.box(2.45, 13.25, 2.45, 13.55, 13.875, 13.55),
        Block.box(6.4, 3.25, 6.4, 9.6, 9, 9.6)
    );

    public SpellLoomBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpellLoomBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof SpellLoomBlockEntity loom && player instanceof ServerPlayer serverPlayer) {
                java.util.UUID session = java.util.UUID.randomUUID();
                var provider = new net.minecraft.world.SimpleMenuProvider(
                    (id, inventory, menuPlayer) -> new com.otectus.arsnspells.menu.SpellLoomMenu(id, inventory, loom, session), loom.getDisplayName());
                serverPlayer.openMenu(provider, buffer -> { buffer.writeBlockPos(pos); buffer.writeUUID(session); });
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof SpellLoomBlockEntity loom) {
                loom.dropContents();
            }
            super.onRemove(state, level, pos, newState, moved);
        }
    }
}
