package com.agent1k.g4duels.action;

import com.agent1k.g4duels.menu.MenuContext;
import com.agent1k.g4duels.text.Text;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class MessageAction extends AbstractAction {
    public MessageAction() { super("message"); }
    @Override public void execute(@NotNull Player player, @NotNull MenuContext context, @NotNull String value) { player.sendMessage(Text.color(value)); }
}
