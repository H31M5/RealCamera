package com.xtracr.realcamera.gui;

import com.xtracr.realcamera.config.BindTarget;
import com.xtracr.realcamera.gui.util.TextureViewport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public interface CategoryHost {
    Minecraft minecraft();
    Font font();
    LayoutConstants layout();
    void hostInitWidgets(int page);
    void hostLoadBindTarget(BindTarget target);
    BindTarget hostGenBindTarget();
    String getNameValue();
    int getPage();
    void screenRemoveWidget(@NonNull GuiEventListener widget);
    
    @Nullable ScreenRectangle getTextureViewArea();
    TextureViewport getNewTextureViewport();
}
