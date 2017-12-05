package crazypants.enderio.machines.integration.jei;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nonnull;

import com.enderio.core.common.util.NNList;

import crazypants.enderio.base.EnderIO;
import crazypants.enderio.base.Log;
import crazypants.enderio.base.power.PowerDisplayUtil;
import crazypants.enderio.base.recipe.IMachineRecipe;
import crazypants.enderio.base.recipe.IMachineRecipe.ResultStack;
import crazypants.enderio.base.recipe.MachineRecipeRegistry;
import crazypants.enderio.base.recipe.painter.AbstractPainterTemplate;
import crazypants.enderio.machines.config.config.ClientConfig;
import crazypants.enderio.machines.machine.painter.ContainerPainter;
import crazypants.enderio.machines.machine.painter.GuiPainter;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.IJeiHelpers;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IDrawableAnimated;
import mezz.jei.api.gui.IDrawableStatic;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.BlankRecipeCategory;
import mezz.jei.api.recipe.BlankRecipeWrapper;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IStackHelper;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import static crazypants.enderio.machines.init.MachineObject.block_painter;
import static crazypants.enderio.machines.machine.painter.ContainerPainter.FIRST_INVENTORY_SLOT;
import static crazypants.enderio.machines.machine.painter.ContainerPainter.FIRST_RECIPE_SLOT;
import static crazypants.enderio.machines.machine.painter.ContainerPainter.NUM_INVENTORY_SLOT;
import static crazypants.enderio.machines.machine.painter.ContainerPainter.NUM_RECIPE_SLOT;
import static crazypants.enderio.util.NbtValue.BLOCKSTATE;

public class PainterRecipeCategory extends BlankRecipeCategory<PainterRecipeCategory.PainterRecipeWrapper> {

  public static final @Nonnull String UID = "Painter";

  // ------------ Recipes

  @SuppressWarnings("null")
  @Nonnull
  private static List<PainterRecipeWrapper> splitRecipes(@Nonnull Collection<IMachineRecipe> recipes, List<ItemStack> validItems) {
    long start = System.nanoTime();
    List<AbstractPainterTemplate<?>> basicPainterTemplates = new ArrayList<AbstractPainterTemplate<?>>();
    for (IMachineRecipe recipe : recipes) {
      if (recipe instanceof AbstractPainterTemplate<?>) {
        basicPainterTemplates.add((AbstractPainterTemplate<?>) recipe);
      }
    }

    List<PainterRecipeWrapper> recipesWrappers = new ArrayList<PainterRecipeWrapper>();
    for (ItemStack target : validItems) {
      for (AbstractPainterTemplate<?> basicPainterTemplate : basicPainterTemplates) {
        if (basicPainterTemplate.isValidTarget(target)) {
          recipesWrappers.add(new PainterRecipeWrapper(basicPainterTemplate, target, new ArrayList<ItemStack>(), new ArrayList<ItemStack>()));
        }
      }
    }

    List<ItemStack> paints = ClientConfig.jeiUseShortenedPainterRecipes.get() ? getLimitedItems() : validItems;

    int count = 0;
    for (ItemStack paint : paints) {
      try {
        for (PainterRecipeWrapper painterRecipeWrapper : recipesWrappers) {
          if (painterRecipeWrapper.recipe.isRecipe(paint, painterRecipeWrapper.target)) {
            for (ResultStack result : painterRecipeWrapper.recipe.getCompletedResult(paint, painterRecipeWrapper.target)) {
              painterRecipeWrapper.results.add(result.item);
              painterRecipeWrapper.paints.add(paint);
              count++;
            }
          }
        }
      } catch (Exception e) {
        Log.warn("PainterRecipeCategory: Error while accessing item '" + paint + "': " + e);
        e.printStackTrace();
      }
    }
    long end = System.nanoTime();

    for (PainterRecipeWrapper painterRecipeWrapper : recipesWrappers) {
      if (painterRecipeWrapper.results.isEmpty()) {
        Log.warn("PainterRecipeCategory: Empty recipe group: " + painterRecipeWrapper.recipe + " for " + painterRecipeWrapper.target);
      }
    }

    Log.info(String.format("PainterRecipeCategory: Added %d painter recipes in %d groups to JEI in %.3f seconds.", count, recipesWrappers.size(), (end - start) / 1000000000d));

    return recipesWrappers;
  }

  public static class PainterRecipeWrapper extends BlankRecipeWrapper {

    final AbstractPainterTemplate<?> recipe;
    final int energyRequired;
    final @Nonnull ItemStack target;
    final @Nonnull List<ItemStack> paints;
    final @Nonnull List<ItemStack> results;

    public PainterRecipeWrapper(@Nonnull AbstractPainterTemplate<?> recipe, @Nonnull ItemStack target, @Nonnull List<ItemStack> paints,
        @Nonnull List<ItemStack> results) {
      this.recipe = recipe;
      this.energyRequired = recipe.getEnergyRequired();
      this.target = target;
      this.paints = paints;
      this.results = results;
    }

    public long getEnergyRequired() { 
      return energyRequired;
    }

    @Override
    public void getIngredients(@Nonnull IIngredients ingredients) {
      List<ItemStack> list = new ArrayList<ItemStack>(paints);
      list.add(target);
      ingredients.setInputs(ItemStack.class, list);

      ingredients.setOutputs(ItemStack.class, results);
    }
    
    @Override
    public void drawInfo(@Nonnull Minecraft minecraft, int recipeWidth, int recipeHeight, int mouseX, int mouseY) {           
      String energyString = PowerDisplayUtil.formatPower(energyRequired) + " " + PowerDisplayUtil.abrevation();
      minecraft.fontRenderer.drawString(energyString, 6, 36, 0x808080, false);    
      GlStateManager.color(1,1,1,1);      
    }
  }
 
  @SuppressWarnings("null")
  public static void register(IModRegistry registry, IJeiHelpers jeiHelpers) {
    registry.addRecipeCategories(new PainterRecipeCategory(jeiHelpers));
    registry.addRecipeClickArea(GuiPainter.class, 155, 42, 16, 16, PainterRecipeCategory.UID);
    registry.addRecipeCategoryCraftingItem(new ItemStack(block_painter.getBlock()), PainterRecipeCategory.UID);

    List<ItemStack> validItems = registry.getIngredientRegistry().getIngredients(ItemStack.class);
    registry.addRecipes(
        splitRecipes(MachineRecipeRegistry.instance.getRecipesForMachine(block_painter.getUnlocalisedName()).values(), validItems), UID);

    registry.getRecipeTransferRegistry().addRecipeTransferHandler(ContainerPainter.class, PainterRecipeCategory.UID, FIRST_RECIPE_SLOT, NUM_RECIPE_SLOT,
        FIRST_INVENTORY_SLOT, NUM_INVENTORY_SLOT);
  }

  // ------------ Category

  //Offsets from full size gui, makes it much easier to get the location correct
  private final static int xOff = 34;
  private final static int yOff = 28;
  
  @Nonnull
  private final IStackHelper stackHelper;
  @Nonnull
  private final IDrawable background;

  @Nonnull
  protected final IDrawableAnimated arror;
  
  public PainterRecipeCategory(IJeiHelpers jeiHelpers) {
    stackHelper = jeiHelpers.getStackHelper();
    IGuiHelper guiHelper = jeiHelpers.getGuiHelper();
    ResourceLocation backgroundLocation = EnderIO.proxy.getGuiTexture("painter");
    background = guiHelper.createDrawable(backgroundLocation, xOff, yOff, 120, 50);

    IDrawableStatic flameDrawable = guiHelper.createDrawable(backgroundLocation, 176, 14, 24, 16);
    arror = guiHelper.createAnimatedDrawable(flameDrawable, 200, IDrawableAnimated.StartDirection.LEFT, false);
  }

  @Override
  public @Nonnull String getUid() {
    return UID;
  }

  @Override
  public @Nonnull String getTitle() {
    String localizedName = block_painter.getBlock().getLocalizedName();
    return localizedName != null ? localizedName : "ERROR";
  }

  @Override
  public @Nonnull IDrawable getBackground() {
    return background;
  }

  @Override
  public void drawExtras(@Nonnull Minecraft minecraft) {
    arror.draw(minecraft, 88 - xOff, 34 - yOff);
  }
  
  @Override
  public void setRecipe(@Nonnull IRecipeLayout recipeLayout, @Nonnull PainterRecipeCategory.PainterRecipeWrapper currentRecipe, @Nonnull IIngredients ingredients) {
      IGuiItemStackGroup guiItemStacks = recipeLayout.getItemStacks();
      guiItemStacks.init(0, true, 67 - xOff - 1, 34 - yOff - 1);
      guiItemStacks.init(1, true, 38 - xOff - 1, 34 - yOff - 1);
      guiItemStacks.init(2, false, 121 - xOff - 1, 34 - yOff - 1);

      guiItemStacks.set(0, currentRecipe.target);

      // Not very nice, but the only way to get correct painting recipes into JEI, it seems.
      try {
        List<ItemStack> paints = new ArrayList<ItemStack>();
        List<ItemStack> results = new ArrayList<ItemStack>();

        IFocus<?> focus = recipeLayout.getFocus();
        Object focusValue = focus.getValue();
        if (focusValue instanceof ItemStack) { //&& !focus.isBlank()
          ItemStack focused = (ItemStack) focusValue;
          if (focus.getMode() == IFocus.Mode.OUTPUT) {
            // JEI is focusing on the output item. Limit the recipe to only the paints that actually give this output item. Needs some extra comparison
            // because we told JEI to ignore paint information, which is ok for crafting and soul binding, but not here.
            for (int i = 0; i < currentRecipe.paints.size(); i++) {
              ItemStack resultStack = currentRecipe.results.get(i);
              ItemStack paintStack = currentRecipe.paints.get(i);
              if (stackHelper.isEquivalent(focused, resultStack) && BLOCKSTATE.getString(focused).equals(BLOCKSTATE.getString(resultStack))) {
                paints.add(paintStack);
                results.add(resultStack);
              }
            }
          } else if (!stackHelper.isEquivalent(focused, currentRecipe.target)) {
            // JEI is focusing on the paint. Limit the output items to things that are painted with this paint.
            for (int i = 0; i < currentRecipe.paints.size(); i++) {
              ItemStack resultStack = currentRecipe.results.get(i);
              ItemStack paintStack = currentRecipe.paints.get(i);
              if (stackHelper.isEquivalent(focused, paintStack)) {
                paints.add(paintStack);
                results.add(resultStack);
              }
            }
          } else {
            // JEI is focusing on a paintable item. If that item also can be used as a paint source, it will display "item+item=anything", which is somewhere
            // between weird and wrong. So remove the recipe "item+item" from the list to get "anything+item=anything".
            for (int i = 0; i < currentRecipe.paints.size(); i++) {
              ItemStack resultStack = currentRecipe.results.get(i);
              ItemStack paintStack = currentRecipe.paints.get(i);
              if (!stackHelper.isEquivalent(focused, paintStack)) {
                paints.add(paintStack);
                results.add(resultStack);
              }
            }
          }
          if (!paints.isEmpty()) {
            guiItemStacks.set(1, paints);
            guiItemStacks.set(2, results);
            return;
          }
        }
      } catch (Throwable t) {
        Log.debug(t.getMessage());
      }

      guiItemStacks.set(1, currentRecipe.paints);
      guiItemStacks.set(2, currentRecipe.results);
  }
  
  private static @Nonnull List<ItemStack> getLimitedItems() {
    NNList<ItemStack> list = new NNList<>();
    for (Block block : new Block[] { Blocks.STONE, Blocks.COBBLESTONE, Blocks.GRASS, Blocks.DIRT, Blocks.PLANKS, Blocks.GLASS, Blocks.STONE_STAIRS,
        Blocks.RED_FLOWER, Blocks.SLIME_BLOCK, Blocks.TNT}) {
      Item item = Item.getItemFromBlock(block);
      if (item != null) {
        for (CreativeTabs tab : item.getCreativeTabs()) {
          item.getSubItems(item, tab, list);
        }
      }
    }
    return list;
  }

}