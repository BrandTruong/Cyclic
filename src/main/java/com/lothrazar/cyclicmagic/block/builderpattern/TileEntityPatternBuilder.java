/*******************************************************************************
 * The MIT License (MIT)
 * 
 * Copyright (C) 2014-2018 Sam Bassett (aka Lothrazar)
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 ******************************************************************************/
package com.lothrazar.cyclicmagic.block.builderpattern;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.lothrazar.cyclicmagic.block.core.TileEntityBaseMachineInvo;
import com.lothrazar.cyclicmagic.capability.EnergyStore;
import com.lothrazar.cyclicmagic.data.BlockPosDim;
import com.lothrazar.cyclicmagic.data.ITilePreviewToggle;
import com.lothrazar.cyclicmagic.data.ITileRedstoneToggle;
import com.lothrazar.cyclicmagic.item.locationgps.ItemLocationGps;
import com.lothrazar.cyclicmagic.util.UtilItemStack;
import com.lothrazar.cyclicmagic.util.UtilParticle;
import com.lothrazar.cyclicmagic.util.UtilShape;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ITickable;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class TileEntityPatternBuilder extends TileEntityBaseMachineInvo implements ITickable, ITilePreviewToggle, ITileRedstoneToggle {

  private final static int MAXIMUM = 32;
  private static final int TIMER_FULL = 20;
  private static final int TIMER_SKIP = 1;
  private int timer = 1;
  private int flipX = 0;
  private int flipY = 0;
  private int flipZ = 0;
  private int rotation = 0;//enum value of Rotation
  private static Map<String, String> blockToItemOverrides = new HashMap<String, String>();
  private static final String NBT_SHAPEINDEX = "shapeindex";
  private int shapeIndex;
  public static final int SLOT_SRCA = 18;
  public static final int SLOT_SRCB = 19;
  public static final int SLOT_TARGET = 20;

  enum RenderType {
    OFF, OUTLINE, PHANTOM, SOLID;
  }

  public static enum Fields {
    TIMER, REDSTONE, RENDERPARTICLES, ROTATION, FLIPX, FLIPY, FLIPZ;
  }

  public TileEntityPatternBuilder() {
    super(9 + 9 + 3);
    this.initEnergy(new EnergyStore(MENERGY), BlockPatternBuilder.FUEL_COST);
    this.setSlotsForBoth();
    syncBlockItemMap();
  }

  private void syncBlockItemMap() {
    //maybe in config one day!?!??! good enough for now
    blockToItemOverrides.put("minecraft:redstone_wire", "minecraft:redstone");
    blockToItemOverrides.put("minecraft:powered_repeater", "minecraft:repeater");
    blockToItemOverrides.put("minecraft:unpowered_repeater", "minecraft:repeater");
    blockToItemOverrides.put("minecraft:powered_comparator", "minecraft:comparator");
    blockToItemOverrides.put("minecraft:unpowered_comparator", "minecraft:comparator");
    blockToItemOverrides.put("minecraft:lit_redstone_ore", "minecraft:redstone_ore");
    blockToItemOverrides.put("minecraft:tripwire", "minecraft:string");
    blockToItemOverrides.put("minecraft:wall_sign", "minecraft:sign");
    blockToItemOverrides.put("minecraft:standing_sign", "minecraft:sign");
    blockToItemOverrides.put("minecraft:lit_furnace", "minecraft:furnace");
  }

  @Override
  @SideOnly(Side.CLIENT)
  public AxisAlignedBB getRenderBoundingBox() {
    return TileEntity.INFINITE_EXTENT_AABB;
  }

  @Override
  public int[] getFieldOrdinals() {
    return super.getFieldArray(Fields.values().length);
  }

  @Override
  public boolean isItemValidForSlot(int index, ItemStack stack) {
    if (index >= 18) {
      return stack.getItem() instanceof ItemLocationGps;
    }
    return true;//dont check for "is item block" stuff like dank null 
  }

  @Override
  public int getFieldCount() {
    return Fields.values().length;
  }

  private int findSlotForMatch(IBlockState stateToMatch) {
    int slot = -1;
    if (stateToMatch == null || stateToMatch.getBlock() == null) {
      return slot;
    }
    String blockKey, itemKey, itemInSlot;
    ItemStack is;
    Item itemFromState;
    for (int i = 0; i < this.getSizeInventory() - 3; i++) {
      is = this.getStackInSlot(i);
      if (UtilItemStack.isEmpty(is)) {
        continue;
      }
      itemFromState = Item.getItemFromBlock(stateToMatch.getBlock());
      if (itemFromState == is.getItem()) {
        //        ModCyclic.logger.log("normal match without map "+stateToMatch.getBlock().getLocalizedName());
        slot = i;//yep it matches
        break;
      }
      //TODO: util class for registry checking
      blockKey = Block.REGISTRY.getNameForObject(stateToMatch.getBlock()).toString();
      //   ModCyclic.logger.log("blockKey   " + blockKey);
      if (blockToItemOverrides.containsKey(blockKey)) {
        itemKey = blockToItemOverrides.get(blockKey);
        itemInSlot = Item.REGISTRY.getNameForObject(is.getItem()).toString();
        //  ModCyclic.logger.log("!stateToMatch. KEY?" + blockKey + " VS " + itemInSlot);
        if (itemKey.equalsIgnoreCase(itemInSlot)) {
          slot = i;
          //   ModCyclic.logger.log(blockKey + "->stateToMatch mapped to an item ->" + itemKey);
          break;
        }
      }
    }
    return slot;
  }

  @Override
  public boolean onlyRunIfPowered() {
    return this.needsRedstone == 1;
  }

  @Override
  public boolean shouldRenderInPass(int pass) {
    return pass < 2;
  }

  @Override
  public void update() {
    // OR maybe projector upgrade
    //and/or new projector block
    if (isRunning() == false) { // it works ONLY if its powered
      return;
    }
    if (this.updateEnergyIsBurning() == false) {
      return;
    }
    timer -= 1;
    if (timer <= 0) { //try build one block
      timer = 0;
      List<BlockPos> shapeSrc = this.getSourceShape();
      List<BlockPos> shapeTarget = this.getTargetShape();
      if (shapeSrc.size() <= 0) {
        return;
      }
      if (this.shapeIndex < 0 || this.shapeIndex >= shapeSrc.size()) {
        this.shapeIndex = 0;
      }
      BlockPos posSrc = shapeSrc.get(shapeIndex);
      BlockPos posTarget = shapeTarget.get(shapeIndex);
      if (this.renderParticles != 0) {
        UtilParticle.spawnParticle(this.getWorld(), EnumParticleTypes.CRIT_MAGIC, posSrc);
        UtilParticle.spawnParticle(this.getWorld(), EnumParticleTypes.CRIT_MAGIC, posTarget);
      }
      IBlockState stateToMatch;
      int slot;
      if (world.isAirBlock(posSrc) == false && world.isAirBlock(posTarget)) {
        stateToMatch = world.getBlockState(posSrc);
        slot = this.findSlotForMatch(stateToMatch);
        if (slot < 0) {
          //cant build this item, skip ahead
          shapeIndex++;//going off end handled above
          return;
        } //EMPTY
        timer = TIMER_FULL;//now start over
        world.setBlockState(posTarget, stateToMatch);
        //build complete, move to next location 
        shapeIndex++;//going off end handled above
        this.decrStackSize(slot, 1);
      }
      else { //src IS air, so skip ahead
        timer = TIMER_SKIP;
        shapeIndex++;//going off end handled above
      }
    }
  }

  public BlockPos getGpsTargetPos(int slot) {
    ItemStack gpsA = this.getStackInSlot(slot);
    BlockPosDim targetA = ItemLocationGps.getPosition(gpsA);
    return targetA == null ? null : targetA.toBlockPos();
  }

  public List<BlockPos> getSourceShape() {
    BlockPos targetA = getGpsTargetPos(SLOT_SRCA);
    BlockPos targetB = getGpsTargetPos(SLOT_SRCB);
    //
    if (targetA == null || targetB == null) {
      return new ArrayList<>();
    }
    return UtilShape.cubeFilledSolid(world, targetA, targetB);
    //    return UtilShape.readAllSolid(world, centerSrc, this.sizeRadius, this.height);
  }

  private BlockPos getCenterSource() {
    BlockPos targetA = getGpsTargetPos(SLOT_SRCA);
    BlockPos targetB = getGpsTargetPos(SLOT_SRCB);
    return new BlockPos(
        (targetA.getX() + targetB.getX()) / 2,
        (targetA.getY() + targetB.getY()) / 2,
        (targetA.getZ() + targetB.getZ()) / 2);
  }

  private BlockPos getCenterTarget() {
    BlockPos otherCenter = getCenterSource();
    if (otherCenter == null)
      return null;
    return this.convertPosSrcToTarget(otherCenter);
  }

  private BlockPos convertPosSrcToTarget(BlockPos posSrc) {
    BlockPos newOrigin = getGpsTargetPos(SLOT_TARGET);
    BlockPos targetA = getGpsTargetPos(SLOT_SRCA);
    BlockPos targetB = getGpsTargetPos(SLOT_SRCB);
    if (newOrigin == null || targetA == null || targetB == null) {
      return null;
    }
    int xOffset = Math.abs(posSrc.getX() - targetA.getX());
    int yOffset = Math.abs(posSrc.getY() - targetA.getY());
    int zOffset = Math.abs(posSrc.getZ() - targetA.getZ());
    return newOrigin.add(xOffset, yOffset, zOffset);
  }

  public List<BlockPos> getTargetShape() {
    List<BlockPos> shapeSrc = getSourceShape();
    List<BlockPos> shapeTarget = new ArrayList<BlockPos>();
    for (BlockPos p : shapeSrc) {
      BlockPos conv = this.convertPosSrcToTarget(new BlockPos(p));
      if (conv != null) {
        shapeTarget.add(conv);
      }
    }
    BlockPos trueCenter = this.getCenterTarget();
    shapeTarget = UtilShape.rotateShape(trueCenter, shapeTarget, this.getRotation());
    //    //flip
    if (getField(Fields.FLIPX) == 1) {
      shapeTarget = UtilShape.flipShape(trueCenter, shapeTarget, EnumFacing.Axis.X);
    }
    if (getField(Fields.FLIPY) == 1) {
      shapeTarget = UtilShape.flipShape(trueCenter, shapeTarget, EnumFacing.Axis.Y);
    }
    if (getField(Fields.FLIPZ) == 1) {
      shapeTarget = UtilShape.flipShape(trueCenter, shapeTarget, EnumFacing.Axis.Z);
    }
    return shapeTarget;
  }

  public Map<BlockPos, IBlockState> getShapeFancy(List<BlockPos> sourceShape, List<BlockPos> targetShape) {
    Map<BlockPos, IBlockState> map = new HashMap<BlockPos, IBlockState>();
    for (int i = 0; i < targetShape.size(); i++) {
      BlockPos src = sourceShape.get(i);
      BlockPos targ = targetShape.get(i);
      if (world.isAirBlock(targ))//dont render on top of thing
        map.put(targ, world.getBlockState(src));
    }
    return map;
  }

  @Override
  public void readFromNBT(NBTTagCompound compound) {
    super.readFromNBT(compound);
    shapeIndex = compound.getInteger(NBT_SHAPEINDEX);
  }

  @Override
  public NBTTagCompound writeToNBT(NBTTagCompound compound) {
    compound.setInteger(NBT_SHAPEINDEX, this.shapeIndex);
    return super.writeToNBT(compound);
  }

  public Rotation getRotation() {
    return Rotation.values()[this.rotation];
  }

  public String getRotationName() {
    switch (this.getRotation()) {
      case CLOCKWISE_90:
        return "90";
      case CLOCKWISE_180:
        return "180";
      case COUNTERCLOCKWISE_90:
        return "270";
      case NONE:
      break;
    }
    return "None";
  }

  public int getField(Fields f) {
    switch (f) {
      case TIMER:
        return this.timer;
      case REDSTONE:
        return this.needsRedstone;
      case RENDERPARTICLES:
        return this.renderParticles;
      case ROTATION:
        return this.rotation;
      case FLIPX:
        return flipX;
      case FLIPY:
        return flipY;
      case FLIPZ:
        return flipZ;
    }
    return 0;
  }

  public void setField(Fields f, int value) {
    //max applies to all fields
    if (value > MAXIMUM && f.ordinal() < Fields.ROTATION.ordinal()) {
      value = MAXIMUM;
    }
    switch (f) {
      case TIMER:
        this.timer = value;
      break;
      case REDSTONE:
        this.needsRedstone = value;
      break;
      case RENDERPARTICLES:
        this.renderParticles = value % RenderType.values().length;
      break;
      case ROTATION:
        this.rotation = value % Rotation.values().length;
      break;
      case FLIPX:
        flipX = value % 2;
      break;
      case FLIPY:
        flipY = value % 2;
      break;
      case FLIPZ:
        flipZ = value % 2;
      break;
    }
  }

  public RenderType getRenderType() {
    return RenderType.values()[this.renderParticles];
  }

  @Override
  public int getField(int id) {
    return getField(Fields.values()[id]);
  }

  @Override
  public void setField(int id, int value) {
    setField(Fields.values()[id], value);
  }

  @Override
  public void toggleNeedsRedstone() {
    int val = (this.needsRedstone + 1) % 2;
    this.setField(Fields.REDSTONE.ordinal(), val);
  }

  @Override
  public boolean isPreviewVisible() {
    return renderParticles == 1;
  }

  @Override
  public List<BlockPos> getShape() {
    return getTargetShape();//special case for this block, not used here
  }
}
