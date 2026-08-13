package com.xtracr.realcamera.gui;

import com.google.common.collect.ImmutableSortedMap;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.xtracr.realcamera.RealCameraCore;
import com.xtracr.realcamera.compat.CompatibilityHelper;
import com.xtracr.realcamera.config.*;
import com.xtracr.realcamera.config.BindTarget.BindConfig;
import com.xtracr.realcamera.config.BindTarget.TargetConfig;
import com.xtracr.realcamera.gui.components.*;
import com.xtracr.realcamera.gui.page.*;
import com.xtracr.realcamera.gui.util.*;
import com.xtracr.realcamera.renderer.state.BuiltModelRecord;
import com.xtracr.realcamera.renderer.state.VertexData;
import com.xtracr.realcamera.util.LocUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec2;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.UnaryOperator;

public final class ModelViewScreen extends Screen implements CategoryHost {
    private static final int SELECTION_COLOR = 0x4F3333CC;
    private static final int SIDE_PANEL_BG = 0xFF444444, CENTER_PANEL_BG = 0xFF222222;
    private static final int DEFAULT_SCALE = 80, MIN_SCALE = 16, MAX_SCALE = 1024;
    private static final int CONFIG_NAME_MAX_LENGTH = 20;
    private LayoutConstants layoutConstants = LayoutConstants.create(0, 0);
    private final int widgetWidth = layoutConstants.widgetWidth(), widgetHeight = layoutConstants.widgetHeight(),
            wideWidgetWidth = layoutConstants.wideWidgetWidth(), compactWidgetWidth = layoutConstants.compactWidgetWidth();
    private int x, y, xSize, ySize, middleWidth, page = 0;
    private InputConstants.Key modifierKey = layoutConstants.modifierKey();
    private boolean initialized;

    private ConfigsPage configsPage;
    private PreviewPage previewPage;
    private DisablePage disablePage;

    private final EditBox nameField = WidgetFactory.textField(font, wideWidgetWidth, widgetHeight, CONFIG_NAME_MAX_LENGTH);
    private final NumberField<Integer> priorityField = NumberField.ofInt(font, widgetWidth - 2, widgetHeight - 2, 0, null);
    private final CycleIconButton pauseButton = new CycleIconButton(0, 16, 0, 2);
    private final CycleIconButton showTextureButton = new CycleIconButton(48, 16, 0, 2).setOnValueChange(_ -> initWidgets(page));
    private final CycleButton<Category> toggleCategoryButton = WidgetFactory.cyclingButton(ImmutableSortedMap.of(
            Category.CONFIGS, LocUtil.MODEL_VIEW_WIDGET(Category.CONFIGS.next().id),
            Category.PREVIEW, LocUtil.MODEL_VIEW_WIDGET(Category.PREVIEW.next().id),
            Category.DISABLE, LocUtil.MODEL_VIEW_WIDGET(Category.DISABLE.next().id)), Category.CONFIGS)
            .withTooltip(category -> WidgetFactory.tooltip(category.next().id))
            .displayOnlyValue()
            .create(0, 0, compactWidgetWidth, widgetHeight, CommonComponents.EMPTY, (_, _) -> initWidgets(0));

    private ScreenRectangle modelViewArea;
    @Nullable
    private ScreenRectangle textureViewArea;
    private double modelX, modelY;
    private double textureX, textureY;
    private int modelScale = DEFAULT_SCALE, textureScale = DEFAULT_SCALE;
    private int selectionRadius = 10;
    private double clickedX = -1, clickedY = -1;
    private int layers = 0;
    private final UVRectangleWidget disableAllWidget = new UVRectangleWidget(0f, 0f, 1f, 1f);
    private float xRot, yRot;
    @Nullable
    private VertexData[][] focusedPolyhedron = new VertexData[0][];
    private String focusedTextureId;

    public ModelViewScreen() {
        super(LocUtil.MODEL_VIEW_TITLE());
    }

    @Override
    protected void init() {
        super.init();
        layoutConstants = LayoutConstants.create(width, height);
        xSize = layoutConstants.xSize();
        ySize = layoutConstants.ySize();
        middleWidth = layoutConstants.middleWidth();
        x = layoutConstants.x();
        y = layoutConstants.y();
        modifierKey = layoutConstants.modifierKey();
        if (!initialized) {
            configsPage = new ConfigsPage(this);
            previewPage = new PreviewPage(this);
            disablePage = new DisablePage(this);
            loadBindTarget(RealCameraCore.currentTarget());
        }
        initWidgets(page);
        initialized = true;
    }

    private void initWidgets(int page) {
        this.page = page;
        if (toggleCategoryButton.getValue() == Category.DISABLE && showTextureButton.getValue() == 0) {
            modelViewArea = new ScreenRectangle(x + xSize / 2, y, middleWidth / 2, ySize);
            textureViewArea = new ScreenRectangle(x + (xSize - middleWidth) / 2, y, middleWidth / 2, ySize);
        } else {
            modelViewArea = new ScreenRectangle(x + (xSize - middleWidth) / 2, y, middleWidth, ySize);
            textureViewArea = null;
            if (disablePage.getFocusedRectWidget() != null && disablePage.getFocusedRectWidget().isFocused()) setFocused(null);
            disablePage.setFocusedRectWidget(null);
        }
        clearWidgets();
        initLeftWidgets();
        if (textureViewArea != null) disablePage.renderableRectWidgets(this::addRenderableWidget, getNewTextureViewport());
        if (toggleCategoryButton.getValue() == Category.DISABLE)
            addRenderableWidget(showTextureButton).setPosition(x + (xSize - middleWidth) / 2 + 4, y + 4);
        addRenderableWidget(pauseButton).setPosition(x + (xSize + middleWidth) / 2 - 38, y + 4);
        addRenderableWidget(new SimpleIconButton(x + (xSize + middleWidth) / 2 - 20, y + 4, 16, 16, 0, 0, _ -> {
            modelScale = textureScale = DEFAULT_SCALE;
            configsPage.setEntityYaw(0);
            configsPage.setEntityPitch(0);
            modelX = modelY = textureX = textureY = 0;
            xRot = yRot = 0;
            layers = 0;
        }));
        initRightWidgets();
    }

    private void initLeftWidgets() {
        GridLayout grid = new GridLayout();
        grid.defaultCellSetting().padding(4, 2, 0, 0);
        LayoutSettings smallSettings = grid.newCellSettings().padding(5, 3, 1, 1);
        GridLayout.RowHelper rows = grid.createRowHelper(2);
        UIFactory uiFactory = new UIFactory(grid, smallSettings, rows, this::addRenderableWidget);
        switch (toggleCategoryButton.getValue()) {
            case CONFIGS -> configsPage.initLeftWidgets(uiFactory);
            case PREVIEW -> previewPage.initLeftWidgets(uiFactory);
            case DISABLE -> disablePage.initLeftWidgets(uiFactory);
            default -> throw new IllegalStateException("Unexpected value: " + toggleCategoryButton.getValue());
        }
        rows.addChild(WidgetFactory.button(LocUtil.MODEL_VIEW_WIDGET("save"), widgetWidth, widgetHeight, button -> {
            if (nameField.getValue().isBlank()) {
                button.setTooltip(Tooltip.create(LocUtil.MODEL_VIEW_TOOLTIP("emptyName").withStyle(ChatFormatting.RED)));
                return;
            }
            if (configsPage.getTextureId().isBlank()) {
                button.setTooltip(Tooltip.create(LocUtil.MODEL_VIEW_TOOLTIP("emptyTextureId").withStyle(ChatFormatting.RED)));
                return;
            }
            if (toggleCategoryButton.getValue() == Category.DISABLE && !disablePage.syncDisableConfig(button, true)) return;
            button.setTooltip(null);
            BindTarget bindTarget = genBindTarget();
            ConfigFile.config().putBindTarget(bindTarget);
            ConfigFile.save();
            initWidgets(page);
        }));
        rows.addChild(priorityField, smallSettings).setTooltip(WidgetFactory.tooltip("priority"));
        boolean editableName = toggleCategoryButton.getValue() == Category.CONFIGS;
        nameField.setEditable(editableName);
        if (!editableName && nameField.isFocused()) nameField.setFocused(false);
        rows.addChild(nameField, 2, smallSettings).setTooltip(WidgetFactory.tooltip("targetName"));
        grid.arrangeElements();
        FrameLayout.alignInRectangle(grid, x, y + 2, x + (xSize - middleWidth) / 2 - 4, y + ySize, 0, 0);
        grid.visitWidgets(this::addRenderableWidget);
    }

    private void initRightWidgets() {
        GridLayout grid = new GridLayout();
        grid.defaultCellSetting().padding(4, 2, 0, 0);
        LayoutSettings smallSettings = grid.newCellSettings().padding(5, 3, 1, 1);
        GridLayout.RowHelper rows = grid.createRowHelper(4);
        rows.addChild(toggleCategoryButton, 3);
        rows.addChild(new SimpleIconButton(80, 0, _ -> {
            if (CompatibilityHelper.isModLoaded("cloth-config")) minecraft.setScreen(ConfigScreen.create(this));
        }), smallSettings).setTooltip(WidgetFactory.tooltip("toConfigScreen"));
        UIFactory uiFactory = new UIFactory(grid, smallSettings, rows, this::addRenderableWidget);
        final int pages;
        // Return category pages
        switch (toggleCategoryButton.getValue()){
            case CONFIGS, PREVIEW -> pages = configsPage.initRightWidgets(uiFactory);
            case DISABLE -> pages = disablePage.initRightWidgets(uiFactory);
            default -> throw new IllegalStateException("Unexpected value: " + toggleCategoryButton.getValue());
        }
        grid.arrangeElements();
        FrameLayout.alignInRectangle(grid, x + (xSize + middleWidth) / 2 + 4, y + 2, x + xSize, y + ySize, 0, 0);
        grid.visitWidgets(this::addRenderableWidget);
        addRenderableWidget(new SimpleIconButton(x + (xSize + middleWidth) / 2 + 8, y + ySize - 20, 16, 16, 16, 0, _ -> initWidgets((page - 1 + pages) % pages)));
        Component pageInfoText = LocUtil.literal((page + 1) + " / " + pages);
        addRenderableWidget(new StringWidget(x + (3 * xSize + middleWidth) / 4 + 2 - font.width(pageInfoText) / 2, y + ySize - 20, font.width(pageInfoText), widgetHeight, pageInfoText, font));
        addRenderableWidget(new SimpleIconButton(x + xSize - 21, y + ySize - 20, 16, 16, 32, 0, _ -> initWidgets((page + 1) % pages)));
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
        if (toggleCategoryButton.getValue() == Category.DISABLE && disablePage.getSelectionMode() == 2 && inModelViewArea(mouseX, mouseY)) {
            GUIHelper.enableScissor(graphics, modelViewArea);
            GUIHelper.fill(graphics, mouseX - selectionRadius, mouseY - selectionRadius, mouseX + selectionRadius, mouseY + selectionRadius, 400, SELECTION_COLOR);
            graphics.disableScissor();
        }
        if (textureViewArea != null) {
            GUIHelper.enableScissor(graphics, textureViewArea);
            if (clickedX >= 0 && clickedY >= 0)
                graphics.fill(Math.min((int) clickedX, mouseX), Math.min((int) clickedY, mouseY), Math.max((int) clickedX, mouseX), Math.max((int) clickedY, mouseY), SELECTION_COLOR);
            if (disablePage.getDisableMode()) {
                disableAllWidget.setViewport(getNewTextureViewport()).extractWidgetRenderState(graphics, mouseX, mouseY, partialTicks);
            }
            graphics.disableScissor();
        }
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.extractBackground(graphics, mouseX, mouseY, partialTicks);
        graphics.fill(x, y, x + (xSize - middleWidth) / 2 - 4, y + ySize, SIDE_PANEL_BG);
        graphics.fill(x + (xSize - middleWidth) / 2, y, x + (xSize + middleWidth) / 2, y + ySize, CENTER_PANEL_BG);
        graphics.fill(x + (xSize + middleWidth) / 2 + 4, y, x + xSize, y + ySize, SIDE_PANEL_BG);
        ModelAnalyser analyser = new ModelAnalyser();
        BindTarget target = genBindTarget();
        target.offsets().scale *= modelScale;
        String textureId = toggleCategoryButton.getValue() == Category.DISABLE ? disablePage.getDisabledTextureId() : "";
        Set<String> hiddenNames = disablePage.getHiddenNames(nameField.getValue());
        assert minecraft.player != null;
        List<BuiltModelRecord> modelRecords = captureRotatedEntity(analyser, target, minecraft.player);
        List<BuiltModelRecord> textureRecords = modelRecords.stream().filter(record -> record.containsTextureId(textureId)).toList();
        ModelAnalyser.applyDisableConfigs(modelRecords, target, textureId, hiddenNames);
        computeFocusedPrimitives(analyser, modelRecords, textureRecords, mouseX, mouseY);
        renderCulledModels(graphics, analyser, target, modelRecords);
        renderFlattenedModels(graphics, analyser, textureRecords);
    }

    private List<BuiltModelRecord> captureRotatedEntity(ModelAnalyser analyser, BindTarget target, LivingEntity entity) {
        CompatibilityHelper.isRenderInScreen = true;
        float entityBodyYaw = entity.yBodyRot;
        float entityYaw = entity.getYRot();
        float entityPitch = entity.getXRot();
        float entityPrevHeadYaw = entity.yHeadRotO;
        float entityHeadYaw = entity.yHeadRot;
        entity.yBodyRot = 180.0f;
        entity.setYRot(180.0f + (float) configsPage.getEntityYaw());
        entity.setXRot((float) configsPage.getEntityPitch());
        entity.yHeadRot = entity.getYRot();
        entity.yHeadRotO = entity.getYRot();
        try {
            int x1 = modelViewArea.left(), y1 = modelViewArea.top(), x2 = modelViewArea.right(), y2 = modelViewArea.bottom();
            Quaternionf rotation = new Quaternionf().rotateX((float) Math.PI / 6 + xRot).rotateY((float) Math.PI / 6 + yRot).rotateZ((float) Math.PI);
            PoseStack modelPose = new PoseStack();
            modelPose.translate((x1 + x2) / 2.0f, (y1 + y2) / 2.0f, 0);
            modelPose.scale(modelScale, modelScale, -modelScale);
            modelPose.translate(modelX, modelY, 0);
            modelPose.mulPose(rotation);
            modelPose.translate(0, -entity.getBbHeight() / 2.0f, 0);
            return analyser.captureModel(minecraft, entity, 1.0f, modelPose, target);
        } finally {
            CompatibilityHelper.isRenderInScreen = false;
            entity.yBodyRot = entityBodyYaw;
            entity.setYRot(entityYaw);
            entity.setXRot(entityPitch);
            entity.yHeadRotO = entityPrevHeadYaw;
            entity.yHeadRot = entityHeadYaw;
        }
    }

    private void computeFocusedPrimitives(ModelAnalyser analyser, List<BuiltModelRecord> modelRecords, List<BuiltModelRecord> textureRecords, int mouseX, int mouseY) {
        if (toggleCategoryButton.getValue() == Category.DISABLE && disablePage.getSelectionMode() == 2 && inModelViewArea(mouseX, mouseY))
            analyser.computeFocusedOnModel(modelRecords, mouseX - selectionRadius, mouseY - selectionRadius, mouseX + selectionRadius, mouseY + selectionRadius);
        if (inTextureViewArea(mouseX, mouseY)) {
            Vec2 mouseUV = getNewTextureViewport().xyToUV(mouseX, mouseY);
            analyser.computeFocusedOnTexture(textureRecords, mouseUV.x, mouseUV.y);
        }
        if (inModelViewArea(mouseX, mouseY)) analyser.computeFocusedOnModel(modelRecords, mouseX, mouseY, layers);
        if (toggleCategoryButton.getValue() == Category.CONFIGS || disablePage.getSelectionMode() == 1) analyser.computeFocusedPolyhedron();
        focusedPolyhedron = analyser.getFocusedPolyhedron();
        focusedTextureId = analyser.getFocusedTextureId();
    }

    private void renderCulledModels(GuiGraphicsExtractor graphics, ModelAnalyser analyser, BindTarget target, List<BuiltModelRecord> modelRecords) {
        int x1 = modelViewArea.left(), y1 = modelViewArea.top(), x2 = modelViewArea.right(), y2 = modelViewArea.bottom();
        GUIHelper.enableScissor(graphics, modelViewArea);
        float invScale = 1.0f / modelScale;
        Matrix4f modelTransform = new Matrix4f();
        modelTransform.scale(invScale, invScale, -invScale);
        modelTransform.translate(-(x1 + x2) / 2.0f, -(y1 + y2) / 2.0f, 0);
        GUIHelper.culledModels(graphics, modelRecords, modelScale, modelTransform, x1, y1, x2, y2);
        if (toggleCategoryButton.getValue() != Category.PREVIEW) analyser.drawFocusedInModelArea(graphics);
        if (toggleCategoryButton.getValue() == Category.CONFIGS) analyser.drawBindTarget(graphics, target, modelScale);
        else analyser.drawCameraDirections(graphics, modelScale);
        graphics.disableScissor();
    }

    private void renderFlattenedModels(GuiGraphicsExtractor graphics, ModelAnalyser analyser, List<BuiltModelRecord> textureRecords) {
        if (textureViewArea == null) return;
        int x1 = textureViewArea.left(), y1 = textureViewArea.top(), x2 = textureViewArea.right(), y2 = textureViewArea.bottom();
        GUIHelper.enableScissor(graphics, textureViewArea);
        float scale = (textureScale * textureViewArea.width()) / (float) DEFAULT_SCALE;
        Vector3f offset = new Vector3f((float) textureX - 0.5f, (float) textureY - 0.5f, 0);
        GUIHelper.flattenedModels(graphics, textureRecords, offset, x1, y1, x2, y2, scale);
        Matrix4f texturePose = new Matrix4f();
        texturePose.translate((x1 + x2) / 2.0f, (y1 + y2) / 2.0f, 0);
        texturePose.scale(scale, scale, -scale);
        texturePose.translate(offset);
        analyser.drawFocusedInTextureArea(graphics, texturePose);
        graphics.disableScissor();
    }

    private void loadBindTarget(BindTarget target) {
        if (target.isEmpty()) return;
        nameField.setValue(target.name());
        priorityField.setNumber(target.priority());
        configsPage.loadBindTarget(target);
        previewPage.loadBindTarget(target);
        disablePage.loadBindTarget(target);
    }

    private BindTarget genBindTarget() {
        TargetConfig targetConfig = configsPage.genTargetConfig();
        BindConfig bindConfig = previewPage.genBindConfig();
        OffsetConfig offsets = previewPage.genOffsetConfig();
        List<DisableConfig> disableConfig = disablePage.genDisableConfigs();
        return new BindTarget(nameField.getValue(), configsPage.getTextureId(), priorityField.getNumber(), previewPage.getDepth(),
                targetConfig, bindConfig, offsets, disableConfig);
    }

    private boolean inModelViewArea(double x, double y) {
        return modelViewArea.containsPoint((int) x, (int) y);
    }

    private boolean inTextureViewArea(double x, double y) {
        return textureViewArea != null && textureViewArea.containsPoint((int) x, (int) y);
    }

    public boolean leftClickedWithModifier(double mouseX, double mouseY) {
        if (focusedPolyhedron.length == 0) return false;
        if (inModelViewArea(mouseX, mouseY) && toggleCategoryButton.getValue() == Category.CONFIGS) {
            configsPage.leftClickedWithModifier(focusedTextureId, focusedPolyhedron);
            return true;
        } else if ((inModelViewArea(mouseX, mouseY) || inTextureViewArea(mouseX, mouseY)) && toggleCategoryButton.getValue() == Category.DISABLE) {
            disablePage.leftClickedWithModifier(focusedTextureId, focusedPolyhedron, this::addRenderableWidget, this::setFocused);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        double storedX = clickedX, storedY = clickedY;
        clickedX = clickedY = -1;
        if (event.input() == InputConstants.MOUSE_BUTTON_LEFT && (toggleCategoryButton.getValue() == Category.CONFIGS || toggleCategoryButton.getValue() == Category.DISABLE)) {
            if (InputConstants.isKeyDown(minecraft.getWindow(), modifierKey.getValue())) {
                if (leftClickedWithModifier(event.x(), event.y())) return true;
            } else if (inTextureViewArea(event.x(), event.y())) {
                if (super.mouseClicked(event, doubleClick)) return true;
                if (storedX >= 0 && storedY >= 0) {
                    float xMin = (float) Math.min(storedX, event.x()), yMin = (float) Math.min(storedY, event.y()), xMax = (float) Math.max(storedX, event.x()), yMax = (float) Math.max(storedY, event.y());
                    TextureViewport viewport = getNewTextureViewport();
                    Vec2 minUV = viewport.xyToUV(xMin, yMin);
                    Vec2 maxUV = viewport.xyToUV(xMax, yMax);
                    setFocused(disablePage.addRectWidget(new UVRectangleWidget(minUV.x, minUV.y, maxUV.x, maxUV.y), this::addRenderableWidget));
                    return true;
                }
                clickedX = (int) event.x();
                clickedY = (int) event.y();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        if (event.input() == InputConstants.MOUSE_BUTTON_LEFT && !InputConstants.isKeyDown(minecraft.getWindow(), modifierKey.getValue())) {
            if (inModelViewArea(event.x(), event.y())) {
                xRot += (float) (Math.PI * dy / ySize);
                yRot -= (float) (Math.PI * dx / middleWidth);
                return true;
            }
        } else if (event.input() == InputConstants.MOUSE_BUTTON_RIGHT) {
            if (inModelViewArea(event.x(), event.y())) {
                modelX += dx / modelScale;
                modelY += dy / modelScale;
                return true;
            }
            if (inTextureViewArea(event.x(), event.y())) {
                textureX += dx / textureScale;
                textureY += dy / textureScale;
                disablePage.setRectWidgetsViewport(getNewTextureViewport());
                return true;
            }
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (inModelViewArea(mouseX, mouseY)) {
            if (InputConstants.isKeyDown(minecraft.getWindow(), modifierKey.getValue())) {
                if (toggleCategoryButton.getValue() == Category.DISABLE && disablePage.getSelectionMode() == 2)
                    selectionRadius = Mth.clamp(selectionRadius + (int) verticalAmount * 2, 2, 48);
                else layers = Math.max(0, layers + (int) verticalAmount);
            } else {
                modelScale = Mth.clamp(modelScale + (int) verticalAmount * modelScale / 16, MIN_SCALE, MAX_SCALE);
            }
            return true;
        } else if (inTextureViewArea(mouseX, mouseY)) {
            textureScale = Mth.clamp(textureScale + (int) verticalAmount * textureScale / 16, MIN_SCALE, MAX_SCALE);
            disablePage.setRectWidgetsViewport(getNewTextureViewport());
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (event.isSelection()) {
            GuiEventListener focused = getFocused();
            if (focused != null && !focused.isFocused()) setFocused(disablePage.getFocusedRectWidget());
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return pauseButton.getValue() == 1;
    }

    // CategoryHost
    @Override
    public Minecraft minecraft() {
        return minecraft;
    }

    @Override
    public Font font() {
        return font;
    }

    @Override
    public LayoutConstants layout() {
        return layoutConstants;
    }

    @Override
    public void hostInitWidgets(int page) {
        initWidgets(page);
    }

    @Override
    public void hostLoadBindTarget(BindTarget target) {
        loadBindTarget(target);
    }

    @Override
    public BindTarget hostGenBindTarget() {
        return genBindTarget();
    }

    @Override
    public String getNameValue() {
        return nameField.getValue();
    }

    @Override
    public int getPage() {
        return page;
    }

    @Override
    public void screenRemoveWidget(@NonNull GuiEventListener widget) {
        removeWidget(widget);
    }

    @Override
    @Nullable
    public ScreenRectangle getTextureViewArea() {
        return textureViewArea;
    }

    @Override
    public TextureViewport getNewTextureViewport() {
        return new TextureViewport(textureViewArea, textureScale, (float) textureX, (float) textureY);
    }

    public record UIFactory (GridLayout grid, LayoutSettings smallSettings, GridLayout.RowHelper rows, UnaryOperator<AbstractWidget> addRenderable){
    }
}
