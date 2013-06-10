
package com.xcompwiz.mystcraft.api.symbol.logic;

import net.minecraft.client.renderer.RenderEngine;
import net.minecraft.world.World;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Adds a moon to the age
 * 
 * @author XCompWiz
 * 
 */
public interface IMoon {

    /**
     * The celestial body's render pass
     */
    @SideOnly(Side.CLIENT)
    public abstract void render(RenderEngine renderEngine, World worldObj, float partialTicks);
}
