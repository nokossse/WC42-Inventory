package wc42;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;

public final class Wc42ChatScreen extends ChatScreen {
	public Wc42ChatScreen(String initial) {
		super(initial);
	}

	@Override
	protected void init() {
		super.init();
		Wc42Chat.placeInput(this.input);
		Wc42Chat.anchorSuggestionsDown(this.commandSuggestions);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		Wc42Chat.placeInput(this.input);
		Wc42Chat.drawInput(graphics);
		this.input.render(graphics, mouseX, mouseY, partialTick);
		Wc42Chat.renderCommandSuggestions(this.commandSuggestions, graphics, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		Wc42Chat.placeSuggestions(this.commandSuggestions);
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		Wc42Chat.placeSuggestions(this.commandSuggestions);
		return super.mouseScrolled(mouseX, mouseY, delta);
	}
}
