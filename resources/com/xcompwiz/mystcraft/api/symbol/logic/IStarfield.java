
package com.xcompwiz.mystcraft.api.symbol.logic;

import net.minecraft.client.renderer.RenderEngine;
import net.minecraft.world.World;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Adds a starfield to the age
 * This is rendered behind all of the other celestial objects
 * 
 * @author XCompWiz
 * 
 */
public interface IStarfield {

    /**
     * The celestial body's render pass
     */
    @SideOnly(Side.CLIENT)
    public abstract void render(RenderEngine renderEngine, World worldObj, float partialTicks);
}
