package org.samo_lego.taterzens.mixin;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.MobSpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerSpawnS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.samo_lego.taterzens.mixin.accessors.MobSpawnS2CPacketAccessor;
import org.samo_lego.taterzens.mixin.accessors.PlayerListS2CPacketAccessor;
import org.samo_lego.taterzens.mixin.accessors.PlayerSpawnS2CPacketAccessor;
import org.samo_lego.taterzens.npc.TaterzenNPC;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;

import static net.minecraft.network.packet.s2c.play.PlayerListS2CPacket.Action.ADD_PLAYER;
import static net.minecraft.network.packet.s2c.play.PlayerListS2CPacket.Action.REMOVE_PLAYER;

/**
 * Used to "fake" the bot entity
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {

    @Shadow public ServerPlayerEntity player;

    @Shadow @Final private MinecraftServer server;

    @Shadow public abstract void sendPacket(Packet<?> packet);

    @Inject(
            method = "sendPacket(Lnet/minecraft/network/Packet;Lio/netty/util/concurrent/GenericFutureListener;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/ClientConnection;send(Lnet/minecraft/network/Packet;Lio/netty/util/concurrent/GenericFutureListener;)V"
            ),
            cancellable = true)
    private void changeEntityType(Packet<?> packet, GenericFutureListener<? extends Future<? super Void>> listener, CallbackInfo ci) {
        if(packet instanceof MobSpawnS2CPacket) {
            World world = player.getEntityWorld();
            Entity entity = world.getEntityById(((MobSpawnS2CPacketAccessor) packet).getId());

            if(!(entity instanceof TaterzenNPC))
                return;

            TaterzenNPC npc = (TaterzenNPC) entity;
            if(npc.getNpcData().entityType == EntityType.PLAYER) {
                PlayerListS2CPacket playerListS2CPacket = new PlayerListS2CPacket();
                ((PlayerListS2CPacketAccessor) playerListS2CPacket).setAction(ADD_PLAYER);
                ((PlayerListS2CPacketAccessor) playerListS2CPacket).setEntries(Collections.singletonList(playerListS2CPacket.new Entry(npc.getGameProfile(), 0, GameMode.SURVIVAL, npc.getName())));


                PlayerSpawnS2CPacket playerSpawnS2CPacket = new PlayerSpawnS2CPacket();
                ((PlayerSpawnS2CPacketAccessor) playerSpawnS2CPacket).setId(npc.getEntityId());
                ((PlayerSpawnS2CPacketAccessor) playerSpawnS2CPacket).setUuid(npc.getUuid());
                ((PlayerSpawnS2CPacketAccessor) playerSpawnS2CPacket).setX(npc.getX());
                ((PlayerSpawnS2CPacketAccessor) playerSpawnS2CPacket).setY(npc.getY());
                ((PlayerSpawnS2CPacketAccessor) playerSpawnS2CPacket).setZ(npc.getZ());
                ((PlayerSpawnS2CPacketAccessor) playerSpawnS2CPacket).setYaw((byte)((int)(npc.bodyYaw * 256.0F / 360.0F)));
                ((PlayerSpawnS2CPacketAccessor) playerSpawnS2CPacket).setPitch((byte)((int)(npc.pitch * 256.0F / 360.0F)));

                this.sendPacket(playerListS2CPacket);
                this.sendPacket(playerSpawnS2CPacket);
                ci.cancel();
            }
            else {
                PlayerListS2CPacket playerListS2CPacket = new PlayerListS2CPacket();
                ((PlayerListS2CPacketAccessor) playerListS2CPacket).setAction(REMOVE_PLAYER);
                ((PlayerListS2CPacketAccessor) playerListS2CPacket).setEntries(Collections.singletonList(playerListS2CPacket.new Entry(npc.getGameProfile(), 0, GameMode.SURVIVAL, npc.getName())));
                this.sendPacket(playerListS2CPacket);

                int id = Registry.ENTITY_TYPE.getRawId(npc.getNpcData().entityType);
                ((MobSpawnS2CPacketAccessor) packet).setEntityTypeId(id);
            }
        }
    }
}
