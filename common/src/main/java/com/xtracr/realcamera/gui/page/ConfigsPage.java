package com.xtracr.realcamera.gui.page;

import com.google.common.collect.ImmutableSortedMap;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.serialization.DataResult;
import com.xtracr.realcamera.RealCameraCore;
import com.xtracr.realcamera.config.BindTarget;
import com.xtracr.realcamera.config.BindTarget.TargetConfig;
import com.xtracr.realcamera.config.ConfigFile;
import com.xtracr.realcamera.config.codec.ConfigCodec;
import com.xtracr.realcamera.gui.*;
import com.xtracr.realcamera.gui.components.DoubleSlider;
import com.xtracr.realcamera.gui.components.NumberField;
import com.xtracr.realcamera.gui.components.SimpleIconButton;
import com.xtracr.realcamera.gui.util.WidgetFactory;
import com.xtracr.realcamera.renderer.state.VertexData;
import com.xtracr.realcamera.util.LocUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;

import java.util.List;

public final class ConfigsPage{
    private final CategoryHost host;
    private final int widgetHeight, compactWidgetWidth;

    private final Button importButton, exportButton;
    private final DoubleSlider entityPitchSlider, entityYawSlider;
    private final NumberField<Float> forwardUField, forwardVField;
    private final NumberField<Float> upwardUField, upwardVField;
    private final NumberField<Float> posUField, posVField;
    private final EditBox textureIdField;
    private final CycleButton<String> selectingButton;

    public ConfigsPage(CategoryHost host){
        this.host = host;
        Font font = host.font();
        int widgetWidth = host.layout().widgetWidth();
        int wideWidgetWidth = host.layout().wideWidgetWidth();
        widgetHeight = host.layout().widgetHeight();
        compactWidgetWidth = host.layout().compactWidgetWidth();
        InputConstants.Key modifierKey = host.layout().modifierKey();

        importButton = WidgetFactory.button(LocUtil.MODEL_VIEW_WIDGET("import"), widgetWidth, widgetHeight, this::importBindTarget);
        exportButton = WidgetFactory.button(LocUtil.MODEL_VIEW_WIDGET("export"), widgetWidth, widgetHeight, this::exportBindTarget);
        entityPitchSlider = WidgetFactory.slider("pitch", wideWidgetWidth, widgetHeight, -90.0, 90.0);
        entityYawSlider = WidgetFactory.slider("yaw", wideWidgetWidth, widgetHeight, -60.0, 60.0);

        forwardUField = WidgetFactory.floatField(font, widgetWidth, widgetHeight, 0);
        forwardVField = WidgetFactory.floatField(font, widgetWidth, widgetHeight, 0);
        upwardUField = WidgetFactory.floatField(font, widgetWidth, widgetHeight, 0);
        upwardVField = WidgetFactory.floatField(font, widgetWidth, widgetHeight, 0);
        posUField = WidgetFactory.floatField(font, widgetWidth, widgetHeight, 0);
        posVField = WidgetFactory.floatField(font, widgetWidth, widgetHeight, 0);

        textureIdField = WidgetFactory.textField(font, wideWidgetWidth, widgetHeight, 1024);
        selectingButton = WidgetFactory.cyclingButton(ImmutableSortedMap.of(
                        "forward", LocUtil.MODEL_VIEW_WIDGET("forwardVector").withStyle(ChatFormatting.GREEN),
                        "upward", LocUtil.MODEL_VIEW_WIDGET("upwardVector").withStyle(ChatFormatting.RED),
                        "pos", LocUtil.MODEL_VIEW_WIDGET("position").withStyle(ChatFormatting.BLUE)), "forward")
                .withTooltip(_ -> WidgetFactory.tooltip("selecting", modifierKey.getDisplayName(), modifierKey.getDisplayName()))
                .create(0, 0, wideWidgetWidth, widgetHeight, LocUtil.MODEL_VIEW_WIDGET("selecting"));
    }

    public void initLeftWidgets(ModelViewScreen.UIFactory uiFactory){
        GridLayout.RowHelper rows = uiFactory.rows();
        LayoutSettings smallSettings = uiFactory.smallSettings();
        rows.addChild(importButton).setTooltip(WidgetFactory.tooltip("import"));
        rows.addChild(exportButton).setTooltip(WidgetFactory.tooltip("export"));
        rows.addChild(entityPitchSlider, 2);
        rows.addChild(entityYawSlider, 2);
        rows.addChild(selectingButton, 2);
        rows.addChild(forwardUField, smallSettings);
        rows.addChild(forwardVField, smallSettings);
        rows.addChild(upwardUField, smallSettings);
        rows.addChild(upwardVField, smallSettings);
        rows.addChild(posUField, smallSettings);
        rows.addChild(posVField, smallSettings);
        rows.addChild(textureIdField, 2, smallSettings).setTooltip(WidgetFactory.tooltip("textureId"));
    }

    public int initRightWidgets(ModelViewScreen.UIFactory uiFactory){
        LayoutSettings smallSettings = uiFactory.smallSettings();
        GridLayout.RowHelper rows = uiFactory.rows();
        final int page = host.getPage();
        final int widgetsPerPage = 8;
        List<BindTarget> fixedTargetList = ConfigFile.config().binding.fixedTargetList.stream().filter(target -> target.name().equals(RealCameraCore.currentTarget().name())).toList();
        List<BindTarget> targetList = ConfigFile.config().binding.targetList;
        final int fixedTargetCount = fixedTargetList.size();
        final int TargetListSize = fixedTargetCount + targetList.size();
        for (int i = page * widgetsPerPage; i < Math.min((page + 1) * widgetsPerPage, TargetListSize); i++) {
            BindTarget target = i < fixedTargetCount ? fixedTargetList.get(i) : targetList.get(i - fixedTargetCount);
            String name = target.name();
            rows.addChild(WidgetFactory.button(LocUtil.literal(name), compactWidgetWidth, widgetHeight, _ -> host.hostLoadBindTarget(target)), 3)
                    .setTooltip(Tooltip.create(name.equals(RealCameraCore.currentTarget().name()) ?
                            LocUtil.literal(name + "\n").append(LocUtil.MODEL_VIEW_WIDGET("currentConfig")) :
                            LocUtil.literal(name))
                    );
            if (i < fixedTargetCount) continue;
            rows.addChild(new SimpleIconButton(48, 0, _ -> {
                targetList.remove(target);
                ConfigFile.save();
                if (target.name().equals(host.getNameValue())) host.hostLoadBindTarget(BindTarget.blank("", ""));
                host.hostInitWidgets(page * widgetsPerPage > TargetListSize - 2 && TargetListSize > 1 ? page - 1 : page);
            }), smallSettings);
        }
        return (TargetListSize - 1) / widgetsPerPage + 1;
    }

    public void loadBindTarget(BindTarget target){
        textureIdField.setValue(target.textureId());
        forwardUField.setNumber(target.targetConfig().forwardU());
        forwardVField.setNumber(target.targetConfig().forwardV());
        upwardUField.setNumber(target.targetConfig().upwardU());
        upwardVField.setNumber(target.targetConfig().upwardV());
        posUField.setNumber(target.targetConfig().posU());
        posVField.setNumber(target.targetConfig().posV());
    }
    
    public TargetConfig genTargetConfig(){
        return new TargetConfig(
                forwardUField.getNumber(),
                forwardVField.getNumber(),
                upwardUField.getNumber(),
                upwardVField.getNumber(),
                posUField.getNumber(),
                posVField.getNumber()
        );
    }
    
    // Private Methods
    private void importBindTarget(Button button) {
        String base64 = host.minecraft().keyboardHandler.getClipboard().strip();
        DataResult<BindTarget> result = ConfigCodec.fromCompressedBase64(base64);
        switch (result) {
            case DataResult.Success<BindTarget> success -> {
                BindTarget target = success.value();
                host.hostLoadBindTarget(target);
                button.setTooltip(Tooltip.create(LocUtil.MODEL_VIEW_TOOLTIP("importSucceeded", LocUtil.literal("'" + target.name() + "'").withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GREEN)));
            }
            case DataResult.Error<BindTarget> error -> {
                String message = error.message();
                button.setTooltip(Tooltip.create(LocUtil.MODEL_VIEW_TOOLTIP("importFailed", message).withStyle(ChatFormatting.RED)));
            }
        }
    }

    private void exportBindTarget(Button button) {
        BindTarget target = host.hostGenBindTarget();
        DataResult<String> result = ConfigCodec.toCompressedBase64(target);
        switch (result) {
            case DataResult.Success<String> success -> {
                String base64 = success.value();
                host.minecraft().keyboardHandler.setClipboard(base64);
                button.setTooltip(Tooltip.create(LocUtil.MODEL_VIEW_TOOLTIP("exportSucceeded").withStyle(ChatFormatting.GREEN)));
            }
            case DataResult.Error<String> error -> {
                String message = error.message();
                button.setTooltip(Tooltip.create(LocUtil.MODEL_VIEW_TOOLTIP("exportFailed", message).withStyle(ChatFormatting.RED)));
            }
        }
    }
    
    // Helper Methods
    public void leftClickedWithModifier(String focusedTextureId, VertexData[][] focusedPolyhedron) {
        float u = 0, v = 0;
        for (VertexData vertex : focusedPolyhedron[0]) {
            u += vertex.u();
            v += vertex.v();
        }
        u /= focusedPolyhedron[0].length;
        v /= focusedPolyhedron[0].length;
        switch (selectingButton.getValue()) {
            case "forward" -> {
                forwardUField.setNumber(u);
                forwardVField.setNumber(v);
            }
            case "upward" -> {
                upwardUField.setNumber(u);
                upwardVField.setNumber(v);
            }
            case "pos" -> {
                posUField.setNumber(u);
                posVField.setNumber(v);
            }
        }
        textureIdField.setValue(focusedTextureId);
    }

    public String getTextureId(){
        return textureIdField.getValue();
    }

    public double getEntityPitch(){
        return entityPitchSlider.getNumber();
    }

    public void setEntityPitch(double value) {
        entityPitchSlider.setNumber(value);
    }
    
    public double getEntityYaw(){
        return entityYawSlider.getNumber();
    }

    public void setEntityYaw(double value) {
        entityYawSlider.setNumber(value);
    }
}
