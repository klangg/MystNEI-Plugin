package me.heldplayer.plugins.nei.mystcraft.client;

import codechicken.lib.gui.GuiDraw;
import com.xcompwiz.lookingglass.api.ILookingGlassAPI;
import com.xcompwiz.mystcraft.api.hook.*;
import com.xcompwiz.mystcraft.api.symbol.IAgeSymbol;
import com.xcompwiz.mystcraft.api.util.ColorGradient;
import com.xcompwiz.mystcraft.client.gui.GuiUtils;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.lang.reflect.Field;
import java.util.*;
import me.heldplayer.plugins.nei.mystcraft.Assets;
import me.heldplayer.plugins.nei.mystcraft.Objects;
import me.heldplayer.plugins.nei.mystcraft.modules.*;
import me.heldplayer.plugins.nei.mystcraft.wrap.MystObjs;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.world.WorldProvider;
import net.specialattack.forge.core.client.GLState;
import net.specialattack.forge.core.client.MC;
import net.specialattack.forge.core.client.gui.GuiHelper;
import net.specialattack.forge.core.config.ConfigValue;
import org.apache.logging.log4j.Level;

/**
 * Class used for integrating into Mystcraft
 *
 * @author heldplayer
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
public final class Integrator {

    public static LinkingAPI linkingAPI;
    public static LinkPropertyAPI linkPropertyAPI;
    public static SymbolAPI symbolAPI;
    public static ItemFactory itemFactory;
    public static RenderAPI renderAPI;
    public static PageAPI pageAPI;
    public static ILookingGlassAPI lookingGlassAPI;

    public static List<ItemStack> allAges = new ArrayList<ItemStack>();
    public static Class<? extends GuiContainer> guiInkMixerClass;
    public static Class<? extends GuiContainer> guiWritingDeskClass;

    private static List<ItemStack> allLinkpanels;
    private static Field agedataField;
    private static Field symbolsField;
    private static Field pagesField;
    private static Class worldProviderMystClass;
    private static Map itemstack_bindings;
    private static Map oredict_bindings;
    private static Map itemId_bindings;
    private static boolean initialized = false;

    private static Set<IModule> modules = new HashSet<IModule>();

    static {
        Integrator.modules.add(new ModuleTechnicalBlocks());
        Integrator.modules.add(new ModuleDecayBlocks());
        Integrator.modules.add(new ModuleCreativePortfolios());
        Integrator.modules.add(new ModulePages());
        Integrator.modules.add(new ModuleLinkingBooks());
        Integrator.modules.add(new ModuleDescriptiveBooks());
        Integrator.modules.add(new ModuleItemSubsets());
        Integrator.modules.add(new ModuleTooltips());
        Integrator.modules.add(new ModuleRecipes());
    }

    private Integrator() {
    }

    public static void setMystcraftAPI(com.xcompwiz.mystcraft.api.APIInstanceProvider api) {
        try {
            Integrator.linkingAPI = (LinkingAPI) api.getAPIInstance("linking-1");
            Integrator.linkPropertyAPI = (LinkPropertyAPI) api.getAPIInstance("linkingprop-1");
            Integrator.symbolAPI = (SymbolAPI) api.getAPIInstance("symbol-1");
            Integrator.itemFactory = (ItemFactory) api.getAPIInstance("itemfact-1");
            Integrator.renderAPI = (RenderAPI) api.getAPIInstance("render-1");
            Integrator.pageAPI = (PageAPI) api.getAPIInstance("page-1");
        } catch (com.xcompwiz.mystcraft.api.exception.APIUndefined e) {
            Objects.log.error("We're missing a Mystcraft API, uh-oh", e);
        } catch (com.xcompwiz.mystcraft.api.exception.APIVersionUndefined e) {
            Objects.log.error("We're missing an essential Mystcraft API, try updating Mystcraft", e);
        } catch (com.xcompwiz.mystcraft.api.exception.APIVersionRemoved e) {
            Objects.log.error("Mystcraft removed an essential API, try updating " + Objects.MOD_NAME, e);
        }
    }

    public static void setLookingGlassAPI(com.xcompwiz.lookingglass.api.APIInstanceProvider api) {
        try {
            Integrator.lookingGlassAPI = (ILookingGlassAPI) api.getAPIInstance("alpha-1");
        } catch (com.xcompwiz.lookingglass.api.APIUndefined e) {
            Objects.log.error("We're missing a LookingGlass API, uh-oh", e);
        } catch (com.xcompwiz.lookingglass.api.APIVersionUndefined e) {
            Objects.log.error("We're missing an essential LookingGlass API, try updating LookingGlass", e);
        } catch (com.xcompwiz.lookingglass.api.APIVersionRemoved e) {
            Objects.log.error("LookingGlass removed an essential API, try updating " + Objects.MOD_NAME, e);
        }
    }

    public static Collection<ConfigValue<?>> getAllConfigValues() {
        HashSet<ConfigValue<?>> result = new HashSet<ConfigValue<?>>();

        for (IModule module : Integrator.modules) {
            Collections.addAll(result, module.getConfigEntries());
        }

        return result;
    }

    public static void reinitialize() {
        if (!Integrator.initialized) {
            return;
        }

        for (IModule module : modules) {
            try {
                Objects.log.log(Level.DEBUG, "Disabling module " + module.getClass().getName());

                module.disable();
            } catch (Exception e) {
                Objects.log.log(Level.ERROR, "Failed disabling module " + module.getClass().getName(), e);
            }
        }

        Integrator.initialized = false;

        try {
            Objects.log.log(Level.DEBUG, "Getting all link panels");
            prepareLinkPanels();
        } catch (Exception ex) {
            Objects.log.log(Level.ERROR, "Failed getting all link panels", ex);
        }

        for (IModule module : Integrator.modules) {
            try {
                Objects.log.log(Level.DEBUG, "Enabling module " + module.getClass().getName());

                module.enable();
            } catch (Exception e) {
                Objects.log.log(Level.ERROR, "Failed enabling module " + module.getClass().getName(), e);
            }
        }

        Integrator.initialized = true;
    }

    private static void prepareLinkPanels() {
        try {
            Class<?> inkEffectsClass = Class.forName("com.xcompwiz.mystcraft.data.InkEffects", false, null);
            Field colormapField = inkEffectsClass.getDeclaredField("colormap");
            // Add all modifiers known to have a colour, this includes mod added modifiers
            HashMap colormap = (HashMap) colormapField.get(null);

            TreeMap map = new TreeMap(new LinkPanelSorter());
            map.putAll(colormap);
            if (!map.containsKey("Following")) {
                map.put("Following", null);
            }

            Object[] keys = map.keySet().toArray(new Object[map.size()]);
            int bin = binary(keys.length);

            Integrator.allLinkpanels = new ArrayList<ItemStack>();

            for (int i = 0; i <= bin; i++) {
                ItemStack is = new ItemStack(MystObjs.page, 1, 0);

                NBTTagCompound compound = new NBTTagCompound();
                NBTTagCompound linkPanelCompound = new NBTTagCompound();

                NBTTagList list = new NBTTagList();

                for (int j = 0; j < keys.length; j++) {
                    if (((i >> j) & 0x1) == 1) {
                        list.appendTag(new NBTTagString((String) keys[j]));
                    }
                }

                linkPanelCompound.setTag("properties", list);

                compound.setTag("linkpanel", linkPanelCompound);

                is.setTagCompound(compound);

                Integrator.allLinkpanels.add(is);
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    private static int binary(int bits) {
        int result = 0;
        for (int i = 0; i < bits; i++) {
            result |= 1 << i;
        }
        return result;
    }

    /**
     * Initialize all NEI features for Mystcraft
     */
    public static void initialize() {
        if (Integrator.initialized) {
            return;
        }

        Objects.log.log(Level.DEBUG, "Initializing Mystcraft Integrator");

        try {
            Objects.log.log(Level.DEBUG, "Getting all link panels");
            prepareLinkPanels();
        } catch (Exception ex) {
            Objects.log.log(Level.ERROR, "Failed getting all link panels", ex);
        }

        try {
            Objects.log.log(Level.DEBUG, "Getting methods and fields");
            getMethodsAndFields();
        } catch (Exception ex) {
            Objects.log.log(Level.ERROR, "Failed getting methods and fields", ex);
        }

        try {
            Objects.log.log(Level.DEBUG, "Getting GUI classes");
            guiInkMixerClass = (Class<? extends GuiContainer>) Class.forName("com.xcompwiz.mystcraft.client.gui.GuiInkMixer");
            guiWritingDeskClass = (Class<? extends GuiContainer>) Class.forName("com.xcompwiz.mystcraft.client.gui.GuiWritingDesk");
        } catch (Exception ex) {
            Objects.log.log(Level.ERROR, "Failed getting GUI classes", ex);
        }

        for (IModule module : Integrator.modules) {
            try {
                Objects.log.log(Level.DEBUG, "Enabling module " + module.getClass().getName());

                module.enable();
            } catch (Exception e) {
                Objects.log.log(Level.ERROR, "Failed enabling module " + module.getClass().getName(), e);
            }
        }

        Integrator.initialized = true;
    }

    /**
     * Gets all methods and fields required by recipe handlers and such to
     * function
     */
    private static void getMethodsAndFields() throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        Class<?> inkEffectsClass = Class.forName("com.xcompwiz.mystcraft.data.InkEffects", false, null);

        Field bindings = inkEffectsClass.getDeclaredField("itemstack_bindings");
        Integrator.itemstack_bindings = (Map) bindings.get(null);

        bindings = inkEffectsClass.getDeclaredField("oredict_bindings");
        Integrator.oredict_bindings = (Map) bindings.get(null);

        bindings = inkEffectsClass.getDeclaredField("itemId_bindings");
        Integrator.itemId_bindings = (Map) bindings.get(null);

        Integrator.worldProviderMystClass = Class.forName("com.xcompwiz.mystcraft.world.WorldProviderMyst", false, null);
        Integrator.agedataField = Integrator.worldProviderMystClass.getDeclaredField("agedata");

        Class<?> ageDataClass = Class.forName("com.xcompwiz.mystcraft.world.agedata.AgeData", false, null);
        Integrator.symbolsField = ageDataClass.getDeclaredField("symbols");
        Integrator.pagesField = ageDataClass.getDeclaredField("pages");
    }

    public static List<ItemStack> getAllLinkpanels() {
        return Integrator.allLinkpanels;
    }

    public static InkMixerRecipe getInkMixerRecipe(ItemStack stack) {
        try {
            if (stack == null) {
                return null;
            }

            Map<String, Float> properties = Integrator.linkPropertyAPI.getPropertiesForItem(stack);

            if (properties == null) {
                return null;
            }

            ColorGradient gradient = Integrator.linkPropertyAPI.getPropertiesGradient(properties);

            String[] modifiers = properties.keySet().toArray(new String[properties.size()]);

            return new InkMixerRecipe(gradient, modifiers);
        } catch (ArrayIndexOutOfBoundsException e) {
            return null;
        } catch (Throwable e) {
            Objects.log.log(Level.WARN, "Failed getting gradient", e);
            return null;
        }
    }

    public static ArrayList getALlInkMixerRecipes() {
        ArrayList result = new ArrayList();

        result.addAll(Integrator.itemstack_bindings.keySet());
        result.addAll(Integrator.oredict_bindings.keySet());
        result.addAll(Integrator.itemId_bindings.keySet());

        return result;
    }

    @SideOnly(Side.CLIENT)
    public static void renderPage(IAgeSymbol symbol, float x, float y, float z, float width, float height) {
        GuiDraw.changeTexture(Assets.bookPageLeft);
        GLState.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GuiHelper.drawTexturedModalRect(x, y, width, height, z, 0.609375F, 0.0F, 0.7265625F, 0.15625F);

        //InternalAPI.render.drawSymbol(x + 0.5F, y + (height + 1.0F - width) / 2.0F, z, width - 1.0F, symbol);
        GuiUtils.drawSymbol(MC.getRenderEngine(), z, symbol, width - 1.0F, x + 0.5F, y + (height + 1.0F - width) / 2.0F);
    }

    public static List<String> getAgeSymbols(WorldProvider provider) throws IllegalAccessException {
        if (!Integrator.worldProviderMystClass.isAssignableFrom(provider.getClass())) {
            return null;
        }

        Object ageData = Integrator.agedataField.get(provider);

        return (List<String>) Integrator.symbolsField.get(ageData);
    }

    public static List<ItemStack> getAgePages(WorldProvider provider) throws IllegalAccessException {
        if (!Integrator.worldProviderMystClass.isAssignableFrom(provider.getClass())) {
            return null;
        }

        Object ageData = Integrator.agedataField.get(provider);

        return (List<ItemStack>) Integrator.pagesField.get(ageData);
    }

}
