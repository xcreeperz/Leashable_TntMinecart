package test.mgx.mixin;

import test.mgx.MgxTest;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.Leashable;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(TntMinecartEntity.class)
public abstract class TntMinecartEntityMixin extends AbstractMinecartEntity implements Leashable {
    protected TntMinecartEntityMixin(EntityType<?> entityType, World world) {
        super(entityType, world);
    }

    @Unique Leashable.LeashData leashData;

    @Nullable
    public Leashable.LeashData getLeashData() {
        return this.leashData;
    }

    public void setLeashData(@Nullable Leashable.LeashData leashData) {
        this.leashData = leashData;
    }

    public Vec3d getLeashOffset(){
        return new Vec3d(0.0F, 0.88F * this.getHeight(), 0.64F * this.getWidth());
    }

    public boolean canUseQuadLeashAttachmentPoint() {
        return true;
    }

    public Vec3d[] getQuadLeashOffsets() {
        return Leashable.createQuadLeashOffsets(this, (double)0.0F, 0.36, 0.25, 0.88);
    }

    public void writeCustomDataToNbt(NbtCompound nbt) {
        this.writeLeashDataToNbt(nbt, this.leashData);
    }

    public void readCustomDataFromNbt(NbtCompound nbt) {
        this.readLeashDataFromNbt(nbt);
    }

    @Override
    public void snapLongLeash() {
        Leashable.super.snapLongLeash();
        MgxTest.LOGGER.info("Leashable snapLongLeash");
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/vehicle/TntMinecartEntity;explode(D)V"), cancellable = true)
    public void onTickExplode(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        if (this.getWorld().isClient) return;
        if(leashData != null) {
            this.updateLeashedMovement();
        }
    }

    @Unique
    // 看了半天源码没有头绪, 直接手搓拉动逻辑
    protected void updateLeashedMovement() {
        // 数据加载有点问题, 只能暂时这么做
        if (leashData.leashHolder == null) {
            this.remove(RemovalReason.KILLED);
            return;
        }
        Entity holder = leashData.leashHolder;
        World world = this.getWorld();
        if (!holder.isAlive() || holder.getWorld() != world) {
            this.detachLeash();
            return;
        }

        float distance = this.distanceTo(holder);
        if (distance > 20.0F) {
            this.detachLeash();
            return;
        }

        // 超过弹性长度
        if (distance > 12.0F) {
            double d = (holder.getX() - this.getX()) / (double)distance;
            double e = (holder.getY() - this.getY()) / (double)distance;
            double f = (holder.getZ() - this.getZ()) / (double)distance;
            this.setVelocity(this.getVelocity().add(Math.copySign(d * d * 0.4, d), Math.copySign(e * e * 0.4, e), Math.copySign(f * f * 0.4, f)));
        // 弹性范围内
        } else if (distance > 4.0F) {
            Vec3d pullDirection = holder.getPos().subtract(this.getPos()).normalize();
            double pullStrengthFactor = Math.min(0.1, (distance - 2.0) * 0.05);
            Vec3d pullVelocity = pullDirection.multiply(pullStrengthFactor);
            this.addVelocity(pullVelocity.x, pullVelocity.y, pullVelocity.z);
        }
    }

    @Inject(method = "explode(Lnet/minecraft/entity/damage/DamageSource;D)V", at = @At("HEAD"), cancellable = true)
    public void onExplode(@Nullable DamageSource damageSource, double power, CallbackInfo ci) {
        if(this.isLeashed()){
            ci.cancel();
        }
    }

    @Inject(method = "onActivatorRail", at = @At("HEAD"), cancellable = true)
    public void onActivatorRail(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "handleFallDamage", at = @At("HEAD"), cancellable = true)
    public void onHandleFallDamage(double fallDistance, float damagePerDistance, DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
        if(fallDistance < 16){
            cir.setReturnValue(super.handleFallDamage(fallDistance, damagePerDistance, damageSource));
        }
    }

//    @Inject(method = "shouldDetonate", at = @At("HEAD"), cancellable = true)
//    private static void onShouldDetonate(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
//        cir.setReturnValue(false);
//    }
}
