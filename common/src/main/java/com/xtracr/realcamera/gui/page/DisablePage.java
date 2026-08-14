package com.xtracr.realcamera.gui.page;


import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.mojang.blaze3d.platform.InputConstants;
import com.xtracr.realcamera.config.BindTarget;
import com.xtracr.realcamera.config.DisableConfig;
import com.xtracr.realcamera.gui.*;

import com.xtracr.realcamera.gui.components.CycleIconButton;
import com.xtracr.realcamera.gui.components.NumberField;
import com.xtracr.realcamera.gui.components.SimpleIconButton;
import com.xtracr.realcamera.gui.components.UVRectangleWidget;
import com.xtracr.realcamera.gui.util.TextureViewport;
import com.xtracr.realcamera.gui.util.WidgetFactory;
import com.xtracr.realcamera.renderer.state.VertexData;
import com.xtracr.realcamera.util.LocUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.network.chat.CommonComponents;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.IntStream;

public final class DisablePage {
    private final CategoryHost host;
    private final int widgetWidth, compactWidgetWidth, widgetHeight;

    private static final int DISABLE_NAME_MAX_LENGTH = 20;

    private final EditBox disabledName;
    private final EditBox disabledTextureId;

    private final NumberField<Float> uMin;
    private final NumberField<Float> uMax;
    private final NumberField<Float> vMin;
    private final NumberField<Float> vMax;

    private final CycleButton<Boolean> disableMode;
    private final CycleButton<Integer> selectionMode;

    private final List<DisableConfig> disableConfigs = new ArrayList<>();    
    private final Map<String, Set<String>> hiddenNameMap = new HashMap<>();

    @Nullable
    private UVRectangleWidget focusedRectWidget;
    private final List<UVRectangleWidget> rectWidgets = new ArrayList<>();
    private final NumberField<Integer> focusedRectIndex;
    private final StringWidget rectWidgetsSize;

    public DisablePage(CategoryHost host) {
        this.host = host;
        Font font = host.font();
        LayoutConstants layout = host.layout();
        InputConstants.Key modifierKey = layout.modifierKey();
        widgetWidth = layout.widgetWidth();
        compactWidgetWidth = layout.compactWidgetWidth();
        widgetHeight = layout.widgetHeight();
        int wideWidgetWidth = layout.wideWidgetWidth();

        disabledTextureId = WidgetFactory.textField(font, wideWidgetWidth, widgetHeight, 1024);
        disabledName = WidgetFactory.textField(font, compactWidgetWidth, widgetHeight, DISABLE_NAME_MAX_LENGTH);
        focusedRectIndex = NumberField.ofInt(font, widgetWidth - 2, widgetHeight - 2, 0, null).setMin(0);
        rectWidgetsSize = new StringWidget(widgetWidth - 22, widgetHeight, CommonComponents.EMPTY, font);

        uMin = WidgetFactory.floatField(font, compactWidgetWidth - 6, widgetHeight, 0).setMin(-1f).setMax(2f);
        uMax = WidgetFactory.floatField(font, compactWidgetWidth - 6, widgetHeight, 0).setMin(-1f).setMax(2f);
        vMin = WidgetFactory.floatField(font, compactWidgetWidth - 6, widgetHeight, 0).setMin(-1f).setMax(2f);
        vMax = WidgetFactory.floatField(font, compactWidgetWidth - 6, widgetHeight, 0).setMin(-1f).setMax(2f);

        disableMode = WidgetFactory.cyclingButton(ImmutableMap.of(
                        true, LocUtil.MODEL_VIEW_WIDGET("all").withStyle(ChatFormatting.GREEN),
                        false, LocUtil.MODEL_VIEW_WIDGET("part").withStyle(ChatFormatting.BLUE)), true)
                .withTooltip(_ -> WidgetFactory.tooltip("disableMode", modifierKey.getDisplayName()))
                .create(0, 0, wideWidgetWidth, widgetHeight, LocUtil.MODEL_VIEW_WIDGET("disableMode"));
        selectionMode = WidgetFactory.cyclingButton(ImmutableSortedMap.of(
                        0, LocUtil.MODEL_VIEW_WIDGET("single"),
                        1, LocUtil.MODEL_VIEW_WIDGET("multiple"),
                        2, LocUtil.MODEL_VIEW_WIDGET("range").withStyle(ChatFormatting.BLUE)), 0)
                .withTooltip(_ -> WidgetFactory.tooltip("selectionMode", modifierKey.getDisplayName(), modifierKey.getDisplayName()))
                .create(0, 0, wideWidgetWidth, widgetHeight, LocUtil.MODEL_VIEW_WIDGET("selectionMode"));
    }

    public void initLeftWidgets(ModelViewScreen.UIFactory uiFactory) {
        GridLayout grid = uiFactory.grid();
        GridLayout.RowHelper rows = uiFactory.rows();
        LayoutSettings smallSettings = uiFactory.smallSettings();
        LayoutSettings offsetXSettings = grid.newCellSettings().padding(-13, 3, 1, 1);
        LinearLayout selectionLayout = LinearLayout.horizontal().spacing(2);
        selectionLayout.defaultCellSetting().alignVertically(0);

        rows.addChild(disableMode, 2);
        rows.addChild(disabledTextureId, 2, smallSettings).setTooltip(WidgetFactory.tooltip("textureId"));
        rows.addChild(selectionMode, 2);
        rows.addChild(focusedRectIndex.setMax(rectWidgets.size()), smallSettings).setOnValueChange(index -> {
            if (index == 0) focusedRectWidget = null;
            else if (index > 0 && index <= rectWidgets.size()) {
                focusedRectWidget = rectWidgets.get(index - 1);
                uMin.setNumber(focusedRectWidget.uMin);
                vMin.setNumber(focusedRectWidget.vMin);
                uMax.setNumber(focusedRectWidget.uMax);
                vMax.setNumber(focusedRectWidget.vMax);
            }
        }).setTooltip(WidgetFactory.tooltip("focusedRectangleNumber"));

        selectionLayout.addChild(new StringWidget(6, widgetHeight, LocUtil.literal("/"), host.font()));
        rectWidgetsSize.setMessage(LocUtil.literal(String.valueOf(rectWidgets.size())));
        selectionLayout.addChild(rectWidgetsSize);
        selectionLayout.addChild(new SimpleIconButton(48, 0, _ -> deleteFocusedRect()),
                        s -> s.paddingLeft(widgetWidth - host.font().width("/" + rectWidgets.size()) - widgetHeight - 2))
                .setTooltip(WidgetFactory.tooltip("deleteSelectedRectangle"));
        rows.addChild(selectionLayout);
        //
        rows.addChild(UVWidget("uMin:"));
        rows.addChild(uMin, offsetXSettings).setOnValueChange(f -> {
            if (focusedRectWidget != null) focusedRectWidget.uMin = f;
        });
        rows.addChild(UVWidget("vMin:"));
        rows.addChild(vMin, offsetXSettings).setOnValueChange(f -> {
            if (focusedRectWidget != null) focusedRectWidget.vMin = f;
        });
        rows.addChild(UVWidget("uMax:"));
        rows.addChild(uMax, offsetXSettings).setOnValueChange(f -> {
            if (focusedRectWidget != null) focusedRectWidget.uMax = f;
        });
        rows.addChild(UVWidget("vMax:"));
        rows.addChild(vMax, offsetXSettings).setOnValueChange(f -> {
            if (focusedRectWidget != null) focusedRectWidget.vMax = f;
        });
    }

    private StringWidget UVWidget(String message) {
        return new StringWidget(26, widgetHeight, LocUtil.literal(message), host.font());
    }

    public int initRightWidgets(ModelViewScreen.UIFactory uiFactory) {
        LayoutSettings smallSettings = uiFactory.smallSettings();
        GridLayout.RowHelper rows = uiFactory.rows();
        LayoutConstants layout = host.layout();
        int widgetsPerPage = 7;
        int size = disableConfigs.size();
        int page = host.getPage();
        int x = layout.x();
        int y = layout.y();
        int xSize = layout.xSize();
        int middleWidth = layout.middleWidth();
        
        rows.addChild(disabledName, 3, smallSettings).setTooltip(WidgetFactory.tooltip("disabledName"));
        rows.addChild(new SimpleIconButton(64, 0, button -> {
            if (syncDisableConfig(button, false)) {
                button.setTooltip(WidgetFactory.tooltip("saveDisableConfig"));
                host.hostInitWidgets(page);
            }
        }), smallSettings).setTooltip(WidgetFactory.tooltip("saveDisableConfig"));
        for (int i = page * widgetsPerPage; i < Math.min((page + 1) * widgetsPerPage, size); i++) {
            DisableConfig config = disableConfigs.get(i);
            String targetName = host.getNameValue();
            Set<String> hiddenNames = hiddenNameMap.computeIfAbsent(targetName, _ -> new HashSet<>());
            uiFactory.addRenderable().apply(new CycleIconButton(32, 16, hiddenNames.contains(config.name()) ? 1 : 0, 2)
                    .setOnValueChange(value -> {
                        if (value == 0) hiddenNames.remove(config.name());
                        else hiddenNames.add(config.name());
                    }))
                    .setPosition(x + (xSize + middleWidth) / 2 - 20, y + 5 + (widgetHeight + 2) * (2 + i % widgetsPerPage));

            rows.addChild(WidgetFactory.button(LocUtil.literal(config.name()), compactWidgetWidth, widgetHeight, _ -> {
                loadDisableConfig(config);
                host.hostInitWidgets(page);
            }), 3).setTooltip(Tooltip.create(LocUtil.literal(config.name())));
            rows.addChild(new SimpleIconButton(48, 0, _ -> {
                disableConfigs.removeIf(disableConfig -> disableConfig.name().equals(config.name()));
                hiddenNameMap.values().forEach(names -> names.remove(config.name()));
                if (disabledName.getValue().equals(config.name())) clearDisableConfig();
                host.hostInitWidgets(page * widgetsPerPage > size - 2 && size > 1 ? page - 1 : page);
            }), smallSettings);
        }
        return (size - 1) / widgetsPerPage + 1;
    }

    public void loadBindTarget(BindTarget target) {
        disableConfigs.clear();
        hiddenNameMap.clear();
        disableConfigs.addAll(target.disableConfigs());
    }

    public List<DisableConfig> genDisableConfigs() {
        DisableConfig currentDisableConfig = new DisableConfig(disabledName.getValue(), disabledTextureId.getValue(), disableMode.getValue(), rectWidgets.stream().map(UVRectangleWidget::toUVRectangle).toList());
        List<DisableConfig> newDisableConfigs = new ArrayList<>(disableConfigs);
        IntStream.range(0, newDisableConfigs.size()).filter(
                        i -> newDisableConfigs.get(i).name().equals(currentDisableConfig.name()))
                .forEach(i -> newDisableConfigs.set(i, currentDisableConfig));
        return newDisableConfigs;
    }

    // Private Methods

    private void clearDisableConfig() {
        disabledName.setValue("");
        disabledTextureId.setValue("");
        focusedRectWidget = null;
        rectWidgets.clear();
        focusedRectIndex.setMax(0);
        focusedRectIndex.setNumber(0);
        rectWidgetsSize.setMessage(LocUtil.literal("0"));
        uMin.setNumber(0f);
        vMin.setNumber(0f);
        uMax.setNumber(0f);
        vMax.setNumber(0f);
    }

    private void loadDisableConfig(DisableConfig config) {
        disabledName.setValue(config.name());
        disabledTextureId.setValue(config.textureId());
        disableMode.setValue(config.disableAll());
        focusedRectWidget = null;
        rectWidgets.clear();
        config.rectangles().forEach(r -> rectWidgets.add(WidgetFactory.rectWidget(r)));
        focusedRectIndex.setMax(rectWidgets.size());
        focusedRectIndex.setNumber(0);
        rectWidgetsSize.setMessage(LocUtil.literal(String.valueOf(rectWidgets.size())));
    }

    private void deleteFocusedRect() {
        if (focusedRectWidget == null) return;
        rectWidgets.remove(focusedRectWidget);
        host.screenRemoveWidget(focusedRectWidget);
        uMin.setNumber(0F);
        vMin.setNumber(0F);
        uMax.setNumber(0F);
        vMax.setNumber(0F);
        focusedRectIndex.setNumber(0);
        focusedRectIndex.setMax(rectWidgets.size());
        rectWidgetsSize.setMessage(LocUtil.literal(String.valueOf(rectWidgets.size())));
    }

    // Helper Methods

    public boolean syncDisableConfig(Button button, boolean autoName) {
        String name = disabledName.getValue();
        String textureId = disabledTextureId.getValue();
        if (name.isBlank()) {
            boolean isMeaningful = !textureId.isBlank() && (disableMode.getValue() || !rectWidgets.isEmpty());
            if (!isMeaningful) {
                return true;
            }
            if (autoName) {
                name = host.getNameValue();
                disabledName.setValue(name);
            } else {
                button.setTooltip(Tooltip.create(LocUtil.MODEL_VIEW_TOOLTIP("emptyName").withStyle(ChatFormatting.RED)));
                return false;
            }
        }
        if (textureId.isBlank()) {
            button.setTooltip(Tooltip.create(LocUtil.MODEL_VIEW_TOOLTIP("emptyTextureId").withStyle(ChatFormatting.RED)));
            return false;
        }
        DisableConfig disableConfig = new DisableConfig(name, textureId, disableMode.getValue(), rectWidgets.stream().map(UVRectangleWidget::toUVRectangle).toList());
        for (int i = 0; i < disableConfigs.size(); i++) {
            if (disableConfigs.get(i).name().equals(name)) {
                disableConfigs.set(i, disableConfig);
                return true;
            }
        }
        disableConfigs.add(disableConfig);
        return true;
    }

    public UVRectangleWidget addRectWidget(UVRectangleWidget rectWidget, Consumer<UVRectangleWidget> addRenderable) {
        UVRectangleWidget foundRectWidget = rectWidgets.stream().filter(r -> r.contains(rectWidget)).findFirst().orElse(null);
        if (foundRectWidget == null) {
            rectWidgets.add(rectWidget);
            focusedRectIndex.setMax(rectWidgets.size());
            rectWidgetsSize.setMessage(LocUtil.literal(String.valueOf(rectWidgets.size())));
            rectWidget.setOnDelete(this::deleteFocusedRect);
            rectWidget.setOnFocusUpdate(() -> {
                focusedRectWidget = rectWidget;
                focusedRectIndex.setNumber(rectWidgets.indexOf(rectWidget) + 1);
                uMin.setNumber(rectWidget.uMin);
                vMin.setNumber(rectWidget.vMin);
                uMax.setNumber(rectWidget.uMax);
                vMax.setNumber(rectWidget.vMax);
            });
            if (host.getTextureViewArea() != null)
                rectWidget.setViewport(host.getNewTextureViewport());
            addRenderable.accept(rectWidget);
            return rectWidget;
        }
        return foundRectWidget;
    }

    public void leftClickedWithModifier(String focusedTextureId, VertexData[][] focusedPolyhedron, Consumer<UVRectangleWidget> addRenderable, Consumer<UVRectangleWidget> setFocused) {
        String disabledId = disabledTextureId.getValue();
        if (disabledId.isBlank() || !focusedTextureId.contains(disabledId)) {
            disabledTextureId.setValue(focusedTextureId);
            return;
        }
        if (selectionMode.getValue() == 2) {
            List<UVRectangleWidget> newRectWidgets = new ArrayList<>();
            for (VertexData[] primitive : focusedPolyhedron) {
                float uMin = 1f, vMin = 1f, uMax = 0, vMax = 0;
                for (VertexData vertex : primitive) {
                    if (vertex.u() < uMin) uMin = vertex.u();
                    if (vertex.v() < vMin) vMin = vertex.v();
                    if (vertex.u() > uMax) uMax = vertex.u();
                    if (vertex.v() > vMax) vMax = vertex.v();
                }
                newRectWidgets.add(new UVRectangleWidget(uMin, vMin, uMax, vMax));
            }
            boolean merged;
            do {
                merged = false;
                for (int i = 0; i < newRectWidgets.size(); i++) {
                    UVRectangleWidget rectWidget = newRectWidgets.get(i);
                    for (int j = i + 1; j < newRectWidgets.size(); ) {
                        if (rectWidget.mergeWith(newRectWidgets.get(j))) {
                            newRectWidgets.remove(j);
                            merged = true;
                        } else {
                            j++;
                        }
                    }
                }
            } while (merged);
            newRectWidgets.forEach(w -> addRectWidget(w, addRenderable));
        } else {
            float uMin = 1f, vMin = 1f, uMax = 0, vMax = 0;
            for (VertexData[] primitive : focusedPolyhedron) {
                for (VertexData vertex : primitive) {
                    if (vertex.u() < uMin) uMin = vertex.u();
                    if (vertex.v() < vMin) vMin = vertex.v();
                    if (vertex.u() > uMax) uMax = vertex.u();
                    if (vertex.v() > vMax) vMax = vertex.v();
                }
            }
            setFocused.accept(addRectWidget(new UVRectangleWidget(uMin, vMin, uMax, vMax), addRenderable));
        }
    }
    
    public String getDisabledTextureId() {
        return disabledTextureId.getValue();
    }

    public int getSelectionMode() {
        return selectionMode.getValue();
    }

    public boolean getDisableMode() {
        return disableMode.getValue();
    }

    @Nullable
    public UVRectangleWidget getFocusedRectWidget() {
        return focusedRectWidget;
    }

    public Set<String> getHiddenNames(String targetName) {
        return hiddenNameMap.getOrDefault(targetName, Set.of());
    }

    public void renderableRectWidgets(Consumer<UVRectangleWidget> addRenderable, TextureViewport view) {
        rectWidgets.forEach(r -> {
            r.setViewport(view);
            addRenderable.accept(r);
        });
    }

    public void setRectWidgetsViewport(TextureViewport view) {
        rectWidgets.forEach(r -> r.setViewport(view));
    }

    public void setFocusedRectWidget(@Nullable UVRectangleWidget rectWidget) {
        focusedRectWidget = rectWidget;
    }
}