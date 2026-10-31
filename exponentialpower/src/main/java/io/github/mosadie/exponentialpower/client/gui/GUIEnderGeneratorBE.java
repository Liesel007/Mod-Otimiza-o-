package io.github.mosadie.exponentialpower.client.gui;

import io.github.mosadie.exponentialpower.ExponentialPower;
import io.github.mosadie.exponentialpower.container.ContainerEnderGeneratorBE;
import io.github.mosadie.exponentialpower.entities.BaseClasses.GeneratorBE;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class GUIEnderGeneratorBE extends AbstractContainerScreen<ContainerEnderGeneratorBE> {
	private final GeneratorBE be;

    private final ResourceLocation GUI = new ResourceLocation(ExponentialPower.MODID, "textures/gui/containerendergeneratorbe.png");

	public GUIEnderGeneratorBE(ContainerEnderGeneratorBE container, Inventory playerInv, Component title) {
        super(container, playerInv, title);

        be = container.getBlockEntity();

        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    // 1.20: as telas desenham com GuiGraphics em vez de PoseStack.
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.render(graphics, mouseX, mouseY, partialTicks);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        graphics.drawString(this.font, Component.translatable("screen.exponentialpower.generator_rate"), 10, 53, 0xffffff);
        graphics.drawString(this.font, be.energy + " RF/t", 10, 63, 0xffffff);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int x, int y) {
        renderBackground(graphics);
        int relX = (this.width - this.imageWidth) / 2;
        int relY = (this.height - this.imageHeight) / 2;
        graphics.blit(GUI, relX, relY, 0, 0, this.imageWidth, this.imageHeight);
    }
}
