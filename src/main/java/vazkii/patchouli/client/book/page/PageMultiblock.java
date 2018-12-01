package vazkii.patchouli.client.book.page;

import javax.annotation.Nonnull;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import com.google.gson.annotations.SerializedName;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.RayTraceResult.Type;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.ForgeHooksClient;
import vazkii.patchouli.api.IMultiblock;
import vazkii.patchouli.api.IStateMatcher;
import vazkii.patchouli.client.base.ClientTicker;
import vazkii.patchouli.client.base.PersistentData;
import vazkii.patchouli.client.base.PersistentData.DataHolder.BookData.Bookmark;
import vazkii.patchouli.client.book.BookEntry;
import vazkii.patchouli.client.book.gui.GuiBook;
import vazkii.patchouli.client.book.gui.GuiBookEntry;
import vazkii.patchouli.client.book.gui.button.GuiButtonBookEye;
import vazkii.patchouli.client.book.page.abstr.PageWithText;
import vazkii.patchouli.client.handler.MultiblockVisualizationHandler;
import vazkii.patchouli.common.multiblock.Multiblock;
import vazkii.patchouli.common.multiblock.MultiblockRegistry;
import vazkii.patchouli.common.multiblock.SerializedMultiblock;

public class PageMultiblock extends PageWithText {

	String name;
	@SerializedName("multiblock_id")
	String multiblockId;
	
	@SerializedName("multiblock")
	SerializedMultiblock serializedMultiblock;

	@SerializedName("enable_visualize")
	boolean showVisualizeButton = true;
	
	transient Multiblock multiblockObj;
	transient GuiButton visualizeButton;

	@Override
	public void build(BookEntry entry, int pageNum) {
		if(multiblockId != null && !multiblockId.isEmpty()) {
			IMultiblock mb = MultiblockRegistry.MULTIBLOCKS.get(new ResourceLocation(multiblockId));
			
			if(mb instanceof Multiblock)
				multiblockObj = (Multiblock) mb;
		}
		
		if(multiblockObj == null && serializedMultiblock != null)
			multiblockObj = serializedMultiblock.toMultiblock();
		
		if(multiblockObj == null)
			throw new IllegalArgumentException("No multiblock located for " + multiblockId);
	}

	@Override
	public void onDisplayed(GuiBookEntry parent, int left, int top) {
		super.onDisplayed(parent, left, top);

		if(showVisualizeButton)
			addButton(visualizeButton = new GuiButtonBookEye(parent, 12, 97));
	}

	@Override
	public int getTextHeight() {
		return 115;
	}
	
	@Override
	public void render(int mouseX, int mouseY, float pticks) {
		int x = GuiBook.PAGE_WIDTH / 2 - 53;
		int y = 7;
		GlStateManager.enableBlend();
		GlStateManager.color(1F, 1F, 1F);
		GuiBook.drawFromTexture(book, x, y, 405, 149, 106, 106);
		
		parent.drawCenteredStringNoShadow(name, GuiBook.PAGE_WIDTH / 2, 0, book.headerColor);

		if(multiblockObj != null)
			renderMultiblock();
		
		super.render(mouseX, mouseY, pticks);
	}

	@Override
	protected void onButtonClicked(GuiButton button) {
		if(button == visualizeButton) {
			String entryKey = parent.getEntry().getResource().toString();
			Bookmark bookmark = new Bookmark(entryKey, pageNum / 2);
			MultiblockVisualizationHandler.setMultiblock(multiblockObj, name, bookmark, true);
			parent.addBookmarkButtons();
			
			if(!PersistentData.data.clickedVisualize) {
				PersistentData.data.clickedVisualize = true;
				PersistentData.save();
			}
		}
	}

	private void renderMultiblock() {
		float maxX = 90;
		float maxY = 90;
		float diag = (float) Math.sqrt(multiblockObj.sizeX * multiblockObj.sizeX + multiblockObj.sizeZ * multiblockObj.sizeZ);
		float height = multiblockObj.sizeY;
		float scaleX = maxX / diag;
		float scaleY = maxY / height;
		float scale = -Math.min(scaleX, scaleY);
		
		int xPos = GuiBook.PAGE_WIDTH / 2;
		int yPos = 60;
		GlStateManager.pushMatrix();
		GlStateManager.translate(xPos, yPos, 100);
		GlStateManager.scale(scale, scale, scale);
		GlStateManager.translate(-(float) multiblockObj.sizeX / 2, -(float) multiblockObj.sizeY / 2, 0);

		GlStateManager.rotate(-30F, 1F, 0F, 0F);
		
		float offX = (float) -multiblockObj.sizeX / 2;
		float offZ = (float) -multiblockObj.sizeZ / 2 + 1;

		float time = parent.ticksInBook * 0.5F;
		if(!GuiScreen.isShiftKeyDown())
			time += ClientTicker.partialTicks;
		GlStateManager.translate(-offX, 0, -offZ);
		GlStateManager.rotate(time, 0F, 1F, 0F);
		GlStateManager.rotate(45F, 0F, 1F, 0F);
		GlStateManager.translate(offX, 0, offZ);
		
		for(int x = 0; x < multiblockObj.sizeX; x++)
			for(int y = 0; y < multiblockObj.sizeY; y++)
				for(int z = 0; z < multiblockObj.sizeZ; z++)
					renderElement(multiblockObj, x, y, z);
		GlStateManager.popMatrix();
	}

	private void renderElement(Multiblock mb, int x, int y, int z) {
		IStateMatcher matcher = mb.stateTargets[x][y][z];
		IBlockState state = matcher.getDisplayedState();
		if(state == null)
			return;

		BlockRendererDispatcher brd = Minecraft.getMinecraft().getBlockRendererDispatcher();
		Minecraft.getMinecraft().renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
		
		GlStateManager.pushMatrix();
		GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.translate(x, y, z);
		GlStateManager.enableBlend();
		GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        boolean errored = false;
        Tessellator.getInstance().getBuffer().begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
        try {
            switch (state.getRenderType()) {
            case MODEL:
                brd.renderBlock(state, BlockPos.ORIGIN.north(), mb, Tessellator.getInstance().getBuffer());
                break;
            }
        } catch (Exception e) {
            errored = true;
            throw new RuntimeException(e); // temp
        } finally {
            if (!errored) {
                Tessellator.getInstance().draw();
            } else {
                Tessellator.getInstance().getBuffer().finishDrawing();
            }
        }
		
        TileEntity te = state.getBlock().createTileEntity(mc.world, state);
        if (te != null && TileEntityRendererDispatcher.instance.getRenderer(te.getClass()) != null) {
            RenderHelper.enableStandardItemLighting();
            GlStateManager.enableLighting();
            TileEntityRendererDispatcher.instance.entityX = 0;
            TileEntityRendererDispatcher.instance.entityY = 0;
            TileEntityRendererDispatcher.instance.entityZ = 0;
            TileEntityRendererDispatcher.staticPlayerX = 0;
            TileEntityRendererDispatcher.staticPlayerY = 0;
            TileEntityRendererDispatcher.staticPlayerZ = 0;

            for (int pass = 0; pass < 2; pass++) {
              ForgeHooksClient.setRenderPass(pass);
              setGlStateForPass(pass, false);
              doTileEntityRenderPass(te, pass);
            }
            ForgeHooksClient.setRenderPass(-1);
            setGlStateForPass(0, false);
            RenderHelper.disableStandardItemLighting();
        }
		GlStateManager.color(1F, 1F, 1F, 1F);
		GlStateManager.enableDepth();
		GlStateManager.popMatrix();
	}

    private void doTileEntityRenderPass(TileEntity tile, final int pass) {
        if (tile != null) {
            if (tile.shouldRenderInPass(pass)) {
                TileEntityRendererDispatcher.instance.render(tile, 0,0,-1, 0);
            }
        }
    }
    
    private void setGlStateForPass(int layer, boolean isNeighbour) {

        GlStateManager.color(1, 1, 1);
        if (isNeighbour) {

            GlStateManager.enableDepth();
            GlStateManager.enableBlend();
            float alpha = 1f;
            float col = 1f;

            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_CONSTANT_COLOR);
            GL14.glBlendColor(col, col, col, alpha);
            return;
        }

        if (layer == 0) {
            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
            GlStateManager.depthMask(true);
        } else {
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.depthMask(false);
        }
    }
}
