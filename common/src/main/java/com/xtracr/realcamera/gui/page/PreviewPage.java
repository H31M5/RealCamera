package com.xtracr.realcamera.gui.page;

import com.google.common.collect.ImmutableMap;
import com.xtracr.realcamera.config.BindTarget;
import com.xtracr.realcamera.config.BindTarget.BindConfig;
import com.xtracr.realcamera.config.ModConfig;
import com.xtracr.realcamera.config.OffsetConfig;
import com.xtracr.realcamera.gui.*;
import com.xtracr.realcamera.gui.components.CycleIconButton;
import com.xtracr.realcamera.gui.components.NumberField;
import com.xtracr.realcamera.gui.components.NumberWidgetPair;
import com.xtracr.realcamera.gui.components.SimpleIconButton;
import com.xtracr.realcamera.gui.util.WidgetFactory;
import com.xtracr.realcamera.util.LocUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.network.chat.CommonComponents;

import java.util.List;

public final class PreviewPage {
    private final CycleButton<Integer> toggleSliderButton;
    private final NumberField<Float> scaleField;
    private final NumberField<Float> depthField;
    private final CycleIconButton bindXButton, bindYButton, bindZButton, bindRotButton;
    private final NumberWidgetPair offsetXPair, offsetYPair, offsetZPair;
    private final NumberWidgetPair offsetPitchPair, offsetYawPair, offsetRollPair;

    private final List<NumberWidgetPair> widgetPairs;


    public PreviewPage(CategoryHost host) {
        Font font = host.font();
        LayoutConstants layout = host.layout();
        int widgetWidth = layout.widgetWidth(), widgetHeight = layout.widgetHeight();
        int wideWidgetWidth = layout.wideWidgetWidth();
        int compactWidgetWidth = layout.compactWidgetWidth();

        bindXButton = new CycleIconButton(16, 16, 1, 2);
        bindYButton = new CycleIconButton(16, 16, 0, 2);
        bindZButton = new CycleIconButton(16, 16, 1, 2);
        bindRotButton = new CycleIconButton(16, 16, 1, 2);

        offsetXPair = new NumberWidgetPair(font, "offsetX", compactWidgetWidth, widgetHeight, ModConfig.MIN_OFFSET_F, ModConfig.MAX_OFFSET_F);
        offsetYPair = new NumberWidgetPair(font, "offsetY", compactWidgetWidth, widgetHeight, ModConfig.MIN_OFFSET_F, ModConfig.MAX_OFFSET_F);
        offsetZPair = new NumberWidgetPair(font, "offsetZ", compactWidgetWidth, widgetHeight, ModConfig.MIN_OFFSET_F, ModConfig.MAX_OFFSET_F);

        offsetPitchPair = new NumberWidgetPair(font, "pitch", compactWidgetWidth, widgetHeight, -180.0f, 180.0f);
        offsetYawPair = new NumberWidgetPair(font, "yaw", compactWidgetWidth, widgetHeight, -180.0f, 180.0f);
        offsetRollPair = new NumberWidgetPair(font, "roll", compactWidgetWidth, widgetHeight, -180.0f, 180.0f);
        
        scaleField = WidgetFactory.floatField(font, widgetWidth, widgetHeight, 1.0f).setMax(64.0f);
        depthField = WidgetFactory.floatField(font, widgetWidth, widgetHeight, 0.2f).setMax(16.0f);

        widgetPairs = List.of(offsetXPair, offsetYPair, offsetZPair,
                offsetPitchPair, offsetYawPair, offsetRollPair);

        toggleSliderButton = WidgetFactory.cyclingButton(ImmutableMap.of(
                0, LocUtil.MODEL_VIEW_WIDGET("toggleSliderToField"),
                1, LocUtil.MODEL_VIEW_WIDGET("toggleFieldToSlider")), 0)
                .displayOnlyValue()
                .create(0, 0, wideWidgetWidth, widgetHeight, CommonComponents.EMPTY, (_, i) -> {
                    boolean useSlider = i == 0;
                    for (NumberWidgetPair pair : widgetPairs) pair.syncAndSwitch(useSlider);
                    host.hostInitWidgets(host.getPage());
                });
    }

    public void initLeftWidgets(ModelViewScreen.UIFactory uiFactory){
        GridLayout grid = uiFactory.grid();
        GridLayout.RowHelper rows = uiFactory.rows();
        LayoutSettings smallSettings = uiFactory.smallSettings();
        
        rows.addChild(toggleSliderButton, 2);
        LayoutSettings numericControlSettings = grid.newCellSettings().padding(-20, 2, 0, 0);
        rows.addChild(bindXButton, smallSettings).setTooltip(WidgetFactory.tooltip("bindButtons"));
        rows.addChild(offsetXPair, numericControlSettings);
        rows.addChild(bindYButton, smallSettings).setTooltip(WidgetFactory.tooltip("bindButtons"));
        rows.addChild(offsetYPair, numericControlSettings);
        rows.addChild(bindZButton, smallSettings).setTooltip(WidgetFactory.tooltip("bindButtons"));
        rows.addChild(offsetZPair, numericControlSettings);
        rows.addChild(bindRotButton, smallSettings).setTooltip(WidgetFactory.tooltip("bindButtons"));
        rows.addChild(offsetPitchPair, numericControlSettings);
        rows.addChild(offsetYawPair, 2, grid.newCellSettings().padding(26, 2, 0, 0));
        rows.addChild(new SimpleIconButton(0, 0, _ -> widgetPairs.forEach(pair -> pair.setNumber(0))), smallSettings);
        rows.addChild(offsetRollPair, numericControlSettings);
        rows.addChild(scaleField, smallSettings).setTooltip(WidgetFactory.tooltip("scale"));
        rows.addChild(depthField, smallSettings).setTooltip(WidgetFactory.tooltip("depth"));
    }
    
    
    public void loadBindTarget(BindTarget target){
        depthField.setNumber(target.disablingDepth());
        bindXButton.setValue(target.bindConfig().bindX() ? 0 : 1);
        bindYButton.setValue(target.bindConfig().bindY() ? 0 : 1);
        bindZButton.setValue(target.bindConfig().bindZ() ? 0 : 1);
        bindRotButton.setValue(target.bindConfig().bindRotation() ? 0 : 1);
        OffsetConfig offsets = target.offsets();
        scaleField.setNumber(offsets.scale);
        offsetXPair.setNumber(offsets.x);
        offsetYPair.setNumber(offsets.y);
        offsetZPair.setNumber(offsets.z);
        offsetPitchPair.setNumber(offsets.pitch);
        offsetYawPair.setNumber(offsets.yaw);
        offsetRollPair.setNumber(offsets.roll);
    }
    
    public BindConfig genBindConfig(){
        return new BindConfig(bindXButton.getValue() == 0,
                bindYButton.getValue() == 0,
                bindZButton.getValue() == 0,
                bindRotButton.getValue() == 0);
    }
    
    public OffsetConfig genOffsetConfig(){
        return new OffsetConfig(scaleField.getNumber(), offsetXPair.getNumber(), offsetYPair.getNumber(), offsetZPair.getNumber(),
                offsetPitchPair.getNumber(), offsetYawPair.getNumber(), offsetRollPair.getNumber());
    }

    public float getDepth(){
        return depthField.getNumber();
    }
}
