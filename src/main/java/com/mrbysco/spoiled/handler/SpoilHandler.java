package com.mrbysco.spoiled.handler;

import com.mrbysco.spoiled.Reference;
import com.mrbysco.spoiled.config.SpoiledConfigCache;
import com.mrbysco.spoiled.registry.SpoilInfo;
import com.mrbysco.spoiled.registry.SpoilRegistry;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.event.TickEvent.WorldTickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SpoilHandler {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onWorldTick(WorldTickEvent event) {
        if(event.phase == TickEvent.Phase.START && !event.world.isRemote && event.world.getGameTime() % SpoiledConfigCache.spoilRate == 0) {
            World world = event.world;
            if(!world.tickableTileEntities.isEmpty()) {
                List<TileEntity> tileEntities = new CopyOnWriteArrayList<>(world.loadedTileEntityList);
                Iterator<TileEntity> iterator;
                for (iterator = tileEntities.iterator(); iterator.hasNext();) {
                    TileEntity te = iterator.next();
                    if(te != null && !te.isRemoved() && te.hasWorld() && te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY).isPresent()) {
                        ResourceLocation location = te.getType().getRegistryName();
                        double spoilRate = 1.0D;
                        if(location != null && (SpoiledConfigCache.containerModifier.containsKey(location))) {
                            spoilRate = SpoiledConfigCache.containerModifier.get(location);
                        }
                        boolean spoilFlag = spoilRate != 0.0D || world.rand.nextDouble() <= spoilRate;
                        if(spoilFlag) {
                            te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY).ifPresent(itemHandler -> {
                                for(int i = 0; i < itemHandler.getSlots(); i++) {
                                    ItemStack stack = itemHandler.getStackInSlot(i);
                                    if(SpoilRegistry.instance().doesSpoil(stack)) {
                                        updateSpoilingStack(stack);

                                        CompoundNBT tag = stack.getOrCreateTag();
                                        if(tag.contains(Reference.SPOIL_TAG) && tag.contains(Reference.SPOIL_TIME_TAG)) {
                                            int getOldTime = tag.getInt(Reference.SPOIL_TAG);
                                            int getMaxTime = tag.getInt(Reference.SPOIL_TIME_TAG);
                                            if(getOldTime >= getMaxTime) {
                                                spoilItemInTE(itemHandler, i, stack);
                                            }
                                        }
                                    }
                                }
                            });
                        }
                    }
                }
            }
        }
    }

    private void spoilItemInTE(IItemHandler itemHandler, int slot, ItemStack stack) {
        SpoilInfo info = SpoilRegistry.instance().getSpoilMap().get(stack.getItem().getRegistryName());
        ItemStack spoiledStack = info.getSpoilStack().copy();
        int oldStackCount = stack.getCount();
        stack.setCount(0);
        if(!spoiledStack.isEmpty()) {
            spoiledStack.setCount(oldStackCount);
            itemHandler.insertItem(slot, spoiledStack, false);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent event) {
        if(event.phase == TickEvent.Phase.END && !event.player.world.isRemote && event.player.world.getGameTime() % SpoiledConfigCache.spoilRate == 0 && !event.player.abilities.isCreativeMode) {
            updateInventory(event.player);
        }
    }

    private void updateInventory(PlayerEntity player) {
        int invCount = player.inventory.getSizeInventory();
        for(int i = 0; i < invCount; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if(!stack.isEmpty()) {
                if(stack.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY).isPresent()) {
                    stack.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY).ifPresent(itemHandler -> {
                        for(int j = 0; j < itemHandler.getSlots(); j++) {
                            ItemStack nestedStack = itemHandler.getStackInSlot(j);
                            if(SpoilRegistry.instance().doesSpoil(nestedStack)) {
                                updateSpoilingStack(nestedStack);

                                CompoundNBT tag = nestedStack.getOrCreateTag();
                                if(tag.contains(Reference.SPOIL_TAG) && tag.contains(Reference.SPOIL_TIME_TAG)) {
                                    int getOldTime = tag.getInt(Reference.SPOIL_TAG);
                                    int getMaxTime = tag.getInt(Reference.SPOIL_TIME_TAG);
                                    if(getOldTime >= getMaxTime) {
                                        spoilItemInTE(itemHandler, j, nestedStack);
                                    }
                                }
                            }
                        }
                    });
                } else {
                    if(SpoilRegistry.instance().doesSpoil(stack)) {
                        updateSpoilingStack(stack);

                        CompoundNBT tag = stack.getOrCreateTag();
                        int getOldTime = tag.getInt(Reference.SPOIL_TAG);
                        int getMaxTime = tag.getInt(Reference.SPOIL_TIME_TAG);
                        if(getOldTime >= getMaxTime) {
                            spoilItemForPlayer(player, stack);
                        }
                    }
                }
            }
        }
    }

    public void updateSpoilingStack(ItemStack stack) {
        SpoilInfo info = SpoilRegistry.instance().getSpoilMap().get(stack.getItem().getRegistryName());
        CompoundNBT tag = stack.getOrCreateTag();
        if(tag.isEmpty()) {
            if(!tag.contains(Reference.SPOIL_TAG)) {
                tag.putInt(Reference.SPOIL_TAG, 0);
            }
            if(!tag.contains(Reference.SPOIL_TIME_TAG)) {
                tag.putInt(Reference.SPOIL_TIME_TAG, info.getSpoilTime());
            }
            stack.setTag(tag);
        } else {
            if(tag.contains(Reference.SPOIL_TAG) && tag.contains(Reference.SPOIL_TIME_TAG)) {
                int getOldTime = tag.getInt(Reference.SPOIL_TAG);
                int getMaxTime = tag.getInt(Reference.SPOIL_TIME_TAG);
                if(getMaxTime != info.getSpoilTime()) {
                    tag.putInt(Reference.SPOIL_TIME_TAG, info.getSpoilTime());
                }
                if(getOldTime < getMaxTime) {
                    getOldTime++;
                    tag.putInt(Reference.SPOIL_TAG, getOldTime);
                    stack.setTag(tag);
                }
            }
        }
    }

    public void spoilItemForPlayer(PlayerEntity player, ItemStack stack) {
        SpoilInfo info = SpoilRegistry.instance().getSpoilMap().get(stack.getItem().getRegistryName());
        ItemStack spoiledStack = info.getSpoilStack().copy();
        int oldStackCount = stack.getCount();
        stack.setCount(0);
        if(!spoiledStack.isEmpty()) {
            spoiledStack.setCount(oldStackCount);
            if(!player.addItemStackToInventory(spoiledStack)) {
                ItemEntity itemEntity = new ItemEntity(player.world, player.getPosX(), player.getPosY(), player.getPosZ());
                itemEntity.setItem(spoiledStack);
                player.world.addEntity(itemEntity);
            }
        }
    }
}
