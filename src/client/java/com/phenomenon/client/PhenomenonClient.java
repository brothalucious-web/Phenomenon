package com.phenomenon.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Renders Sandevistan afterimages by drawing the player's own skin/model at
 * several recent positions. History is local-render-only; no fake entities are
 * added to the world or sent to the server.
 */
public final class PhenomenonClient implements ClientModInitializer {
    private static final int MAX_FRAMES = 7;
    private static final double MIN_MOVEMENT_SQUARED = 0.0025;
    private static final Deque<Vec3d> frames = new ArrayDeque<>();
    private static Vec3d lastPosition;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            AbstractClientPlayerEntity player = client.player;
            if (player == null || client.world == null || (!player.hasStatusEffect(StatusEffects.SPEED) || player.getStatusEffect(StatusEffects.SPEED).getAmplifier() != 1)) {
                frames.clear();
                lastPosition = null;
                return;
            }

            Vec3d current = player.getPos();
            if (lastPosition != null && current.squaredDistanceTo(lastPosition) > MIN_MOVEMENT_SQUARED) {
                frames.addFirst(lastPosition);
                while (frames.size() > MAX_FRAMES) frames.removeLast();
            }
            lastPosition = current;
        });

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            AbstractClientPlayerEntity player = client.player;
            if (player == null || client.world == null || context.consumers() == null
                    || !player.hasStatusEffect(StatusEffects.SPEED) || frames.isEmpty()) {
                return;
            }

            MatrixStack matrices = context.matrixStack();
            VertexConsumerProvider consumers = context.consumers();
            Vec3d camera = context.camera().getPos();

            // Draw oldest copies first so the current player remains visually dominant.
            Vec3d[] history = frames.toArray(new Vec3d[0]);
            for (int i = history.length - 1; i >= 0; i--) {
                Vec3d pos = history[i];
                double x = pos.x - camera.x;
                double y = pos.y - camera.y;
                double z = pos.z - camera.z;
                matrices.push();
                try {
                    // Tiny scale steps create a layered motion-streak effect like the reference.
                    float scale = 0.985F + (history.length - i) * 0.002F;
                    matrices.translate(x, y, z);
                    matrices.scale(scale, scale, scale);
                    client.getEntityRenderDispatcher().render(
                            player, 0.0, 0.0, 0.0, 1.0F,
                            matrices, consumers, 0x00F000F0
                    );
                } finally {
                    matrices.pop();
                }
            }
        });
    }
}
