package com.phenomenon;

import com.mojang.brigadier.Command;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class PhenomenonMod implements ModInitializer {
    private static final int EVENT_MIN_TICKS = 600;
    private static final int EVENT_RANDOM_EXTRA_TICKS = 1200;
    private static final int EFFECT_TICKS = 160;
    private static final int TRAIL_TICKS = 240;
    private static final int TNT_NEARBY_CAP = 24;
    private static final int MOVEMENT_FRAMES = 8;
    private static final Map<UUID, Integer> gravityTicks = new HashMap<>();
    private static final Map<UUID, Integer> tntTrailTicks = new HashMap<>();
    private static final Map<UUID, Integer> tntDelay = new HashMap<>();
    private static final Map<UUID, Integer> speedsterTicks = new HashMap<>();
    private static final Map<UUID, Deque<Vec3d>> movementFrames = new HashMap<>();
    private static final Map<UUID, Vec3d> lastPositions = new HashMap<>();
    private static int eventCooldown = EVENT_MIN_TICKS;
    private static int tickRateRestoreTicks;

    @Override public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(CommandManager.literal("phenomenon")
                .then(CommandManager.literal("random").executes(ctx -> { var p=ctx.getSource().getPlayerOrThrow(); triggerRandom(p.getServerWorld(),p); return Command.SINGLE_SUCCESS; }))
                .then(CommandManager.literal("gravity").executes(ctx -> { var p=ctx.getSource().getPlayerOrThrow(); startGravity(p); msg(p,"GRAVITY INVERSION"); return Command.SINGLE_SUCCESS; }))
                .then(CommandManager.literal("fast").executes(ctx -> { setTickRate(ctx.getSource().getServer(),45); ctx.getSource().sendFeedback(()->Text.literal("PHENOMENON: time accelerated to 45 TPS."),false); return Command.SINGLE_SUCCESS; }))
                .then(CommandManager.literal("slow").executes(ctx -> { setTickRate(ctx.getSource().getServer(),5); ctx.getSource().sendFeedback(()->Text.literal("PHENOMENON: time slowed to 5 TPS."),false); return Command.SINGLE_SUCCESS; }))
                .then(CommandManager.literal("tnt").executes(ctx -> { var p=ctx.getSource().getPlayerOrThrow(); tntTrailTicks.put(p.getUuid(),TRAIL_TICKS); tntDelay.put(p.getUuid(),0); msg(p,"TNT TRAIL"); return Command.SINGLE_SUCCESS; }))
                .then(CommandManager.literal("afterimage").executes(ctx -> { var p=ctx.getSource().getPlayerOrThrow(); speedsterTicks.put(p.getUuid(),TRAIL_TICKS); movementFrames.put(p.getUuid(),new ArrayDeque<>()); lastPositions.put(p.getUuid(),p.getPos()); p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED,TRAIL_TICKS,1,false,false,false)); msg(p,"AFTER IMAGE ENGAGED"); return Command.SINGLE_SUCCESS; }))
                .then(CommandManager.literal("levitate").executes(ctx -> { var p=ctx.getSource().getPlayerOrThrow(); p.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION,EFFECT_TICKS,1)); msg(p,"LEVITATION ANOMALY"); return Command.SINGLE_SUCCESS; }))
                .then(CommandManager.literal("teleport").executes(ctx -> { var p=ctx.getSource().getPlayerOrThrow(); teleportAnomaly(p.getServerWorld(),p); return Command.SINGLE_SUCCESS; }))
                .then(CommandManager.literal("darkness").executes(ctx -> { var p=ctx.getSource().getPlayerOrThrow(); p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS,EFFECT_TICKS,0)); msg(p,"VISUAL INTERFERENCE"); return Command.SINGLE_SUCCESS; }))
                .then(CommandManager.literal("invert").executes(ctx -> { var p=ctx.getSource().getPlayerOrThrow(); invertControls(p); msg(p,"CONTROLS INVERTED"); return Command.SINGLE_SUCCESS; }))
                .then(CommandManager.literal("drop").executes(ctx -> { var p=ctx.getSource().getPlayerOrThrow(); dropInventory(p); msg(p,"INVENTORY EJECTED"); return Command.SINGLE_SUCCESS; }))
                .then(CommandManager.literal("stop").executes(ctx -> { stopPhenomena(ctx.getSource().getServer()); ctx.getSource().sendFeedback(()->Text.literal("PHENOMENON: active effects stopped; normal tick rate restored."),false); return Command.SINGLE_SUCCESS; }))));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> { UUID id=handler.player.getUuid(); gravityTicks.remove(id); tntTrailTicks.remove(id); tntDelay.remove(id); speedsterTicks.remove(id); movementFrames.remove(id); lastPositions.remove(id); handler.player.setNoGravity(false); });
        ServerTickEvents.END_SERVER_TICK.register(PhenomenonMod::tick);
    }

    private static void tick(MinecraftServer server) {
        if (--eventCooldown <= 0) { eventCooldown=EVENT_MIN_TICKS+server.getOverworld().random.nextInt(EVENT_RANDOM_EXTRA_TICKS+1); var players=server.getPlayerManager().getPlayerList(); if(!players.isEmpty()){var p=players.get(server.getOverworld().random.nextInt(players.size()));triggerRandom(p.getServerWorld(),p);} }
        if(tickRateRestoreTicks>0 && --tickRateRestoreTicks==0){runTickRateCommand(server,20);broadcast(server,"PHENOMENON: time returned to normal.");}
        for(ServerPlayerEntity p:server.getPlayerManager().getPlayerList()){
            UUID id=p.getUuid(); ServerWorld w=p.getServerWorld(); Integer g=gravityTicks.get(id);
            if(g!=null){if(g<=1||p.isDead()||p.isSpectator()){gravityTicks.remove(id);p.setNoGravity(false);p.sendMessage(Text.literal("PHENOMENON: gravity restored."),true);}else{gravityTicks.put(id,g-1);p.setNoGravity(true);Vec3d v=p.getVelocity();p.setVelocity(v.x,Math.min(.75,v.y+.08),v.z);p.velocityModified=true;if(w.getTime()%3==0)w.spawnParticles(ParticleTypes.REVERSE_PORTAL,p.getX(),p.getY()+.7,p.getZ(),3,.25,.4,.25,.02);}}
            Integer t=tntTrailTicks.get(id); if(t!=null){if(t<=1){tntTrailTicks.remove(id);tntDelay.remove(id);}else{tntTrailTicks.put(id,t-1);int d=tntDelay.getOrDefault(id,0);if(d>0)tntDelay.put(id,d-1);else if(p.getVelocity().horizontalLengthSquared()>.0025){long nearby=w.getEntitiesByClass(TntEntity.class,p.getBoundingBox().expand(24),e->true).size();if(nearby<TNT_NEARBY_CAP){TntEntity e=new TntEntity(w,p.getX(),p.getY(),p.getZ(),p);e.setFuse(50);w.spawnEntity(e);}tntDelay.put(id,5);}}}
            Integer s=speedsterTicks.get(id);if(s!=null){if(s<=1){speedsterTicks.remove(id);movementFrames.remove(id);lastPositions.remove(id);}else{speedsterTicks.put(id,s-1);Vec3d now=p.getPos(),old=lastPositions.put(id,now);Deque<Vec3d> frames=movementFrames.computeIfAbsent(id,k->new ArrayDeque<>());if(old!=null&&now.squaredDistanceTo(old)>.0004)frames.addLast(old);while(frames.size()>MOVEMENT_FRAMES)frames.removeFirst();}}
        }
    }
    private static void triggerRandom(ServerWorld w,ServerPlayerEntity p){switch(w.random.nextInt(10)){case 0->{startGravity(p);msg(p,"GRAVITY INVERSION");}case 1->{setTickRate(p.getServer(),45);broadcast(p.getServer(),"PHENOMENON: TIME ACCELERATION");}case 2->{setTickRate(p.getServer(),5);broadcast(p.getServer(),"PHENOMENON: TIME DILATION");}case 3->{tntTrailTicks.put(p.getUuid(),TRAIL_TICKS);tntDelay.put(p.getUuid(),0);msg(p,"TNT TRAIL");}case 4->{speedsterTicks.put(p.getUuid(),TRAIL_TICKS);movementFrames.put(p.getUuid(),new ArrayDeque<>());lastPositions.put(p.getUuid(),p.getPos());p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED,TRAIL_TICKS,1,false,false,false));msg(p,"AFTER IMAGE");}case 5->{p.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION,120,1));msg(p,"LEVITATION ANOMALY");}case 6->teleportAnomaly(w,p);case 7->{p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS,EFFECT_TICKS,0));msg(p,"VISUAL INTERFERENCE");}case 8->{invertControls(p);msg(p,"CONTROLS INVERTED");}default->{dropInventory(p);msg(p,"INVENTORY EJECTED");}}}
    private static void invertControls(ServerPlayerEntity p){p.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE,EFFECT_TICKS,10,false,false,false));}
    private static void dropInventory(ServerPlayerEntity p){var inventory=p.getInventory();for(int i=0;i<inventory.size();i++){var stack=inventory.getStack(i);if(!stack.isEmpty()){p.dropItem(stack.copy(),true,false);inventory.setStack(i,net.minecraft.item.ItemStack.EMPTY);}}p.currentScreenHandler.sendContentUpdates();}
    private static void startGravity(ServerPlayerEntity p){gravityTicks.put(p.getUuid(),EFFECT_TICKS);p.setNoGravity(true);Vec3d v=p.getVelocity();p.setVelocity(v.x,Math.max(.25,v.y),v.z);p.velocityModified=true;p.getServerWorld().spawnParticles(ParticleTypes.REVERSE_PORTAL,p.getX(),p.getY()+.6,p.getZ(),18,.6,.3,.6,.08);}
    private static void teleportAnomaly(ServerWorld w,ServerPlayerEntity p){double a=w.random.nextDouble()*Math.PI*2,r=5+w.random.nextDouble()*8;p.requestTeleport(p.getX()+Math.cos(a)*r,p.getY()+1,p.getZ()+Math.sin(a)*r);msg(p,"SPATIAL DISPLACEMENT");}
    private static void setTickRate(MinecraftServer s,int rate){runTickRateCommand(s,rate);tickRateRestoreTicks=160;}
    private static void runTickRateCommand(MinecraftServer s,int rate){s.getCommandManager().executeWithPrefix(s.getCommandSource(),"tick rate "+rate);}
    private static void msg(ServerPlayerEntity p,String e){p.sendMessage(Text.literal("PHENOMENON: "+e),false);}
    private static void broadcast(MinecraftServer s,String m){for(ServerPlayerEntity p:s.getPlayerManager().getPlayerList())p.sendMessage(Text.literal(m),false);}
    private static void stopPhenomena(MinecraftServer s){for(ServerPlayerEntity p:s.getPlayerManager().getPlayerList())p.setNoGravity(false);gravityTicks.clear();tntTrailTicks.clear();tntDelay.clear();speedsterTicks.clear();movementFrames.clear();lastPositions.clear();tickRateRestoreTicks=0;runTickRateCommand(s,20);}
}
