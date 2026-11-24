package com.murderhecoded.ui;

import com.murderhecoded.domain.AgentSnapshot;
import com.murderhecoded.domain.Item;
import com.murderhecoded.domain.ItemSnapshot;
import com.murderhecoded.domain.ItemType;
import com.murderhecoded.domain.KnowledgeSnapshot;
import com.murderhecoded.domain.Location;
import com.murderhecoded.domain.LocationSnapshot;
import com.murderhecoded.domain.SimulationStateSnapshot;
import com.murderhecoded.repository.SimulationRepository;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@SuppressWarnings("PMD")
public class ItemDetailPresenter {

    public record ItemTreeItemData(String label, String itemId, boolean isCategory) {
        @Override
        public String toString() {
            return label;
        }
    }

    public static String updateItemTreeView(TreeView<ItemTreeItemData> itemTreeView,
                                            SimulationRepository state,
                                            int currentStep,
                                            String selectedItemId) {
        if (itemTreeView == null) {
            return selectedItemId;
        }

        SimulationStateSnapshot snap = state.getSnapshotAt(currentStep);

        TreeItem<ItemTreeItemData> root = new TreeItem<>(new ItemTreeItemData("Items", null, true));
        root.setExpanded(true);

        TreeItem<ItemTreeItemData> weaponsBranch = new TreeItem<>(new ItemTreeItemData("🗡️ Weapons", null, true));
        weaponsBranch.setExpanded(true);

        TreeItem<ItemTreeItemData> cluesBranch = new TreeItem<>(new ItemTreeItemData("🔍 Clues & Evidence", null, true));
        cluesBranch.setExpanded(true);

        TreeItem<ItemTreeItemData> valuablesBranch = new TreeItem<>(new ItemTreeItemData("💎 Valuables", null, true));
        valuablesBranch.setExpanded(true);

        TreeItem<ItemTreeItemData> notebooksBranch = new TreeItem<>(new ItemTreeItemData("📓 Notebooks & Documents", null, true));
        notebooksBranch.setExpanded(true);

        TreeItem<ItemTreeItemData> othersBranch = new TreeItem<>(new ItemTreeItemData("📦 Other Items", null, true));
        othersBranch.setExpanded(true);

        TreeItem<ItemTreeItemData> itemToSelect = null;
        String newSelectedId = selectedItemId;

        Collection<ItemSnapshot> itemsList = snap.items().values();

        for (ItemSnapshot item : itemsList) {
            StringBuilder label = new StringBuilder(item.name());

            if (item.currentLocationId() != null) {
                if (item.currentLocationId().startsWith("agent_")) {
                    String carrierName = state.resolveAgentName(item.currentLocationId(), currentStep);
                    label.append(" [🎒 ").append(carrierName).append("]");
                } else {
                    String placeName = state.resolveLocationName(item.currentLocationId(), currentStep);
                    label.append(" [📍 ").append(placeName).append("]");
                }
            }

            if (item.hidden()) {
                label.append(" 🙈");
            }
            if (item.poisoned() || item.poisonConsumed()) {
                label.append(" ☠️");
            }

            TreeItem<ItemTreeItemData> leaf = new TreeItem<>(new ItemTreeItemData(label.toString(), item.id(), false));

            if (item.type() == null) {
                othersBranch.getChildren().add(leaf);
            } else {
                switch (item.type()) {
                    case WEAPON -> weaponsBranch.getChildren().add(leaf);
                    case CLUE -> cluesBranch.getChildren().add(leaf);
                    case VALUABLE -> valuablesBranch.getChildren().add(leaf);
                    case NOTEBOOK -> notebooksBranch.getChildren().add(leaf);
                }
            }

            if (item.id().equals(newSelectedId)) {
                itemToSelect = leaf;
            }
        }

        if (!weaponsBranch.getChildren().isEmpty()) root.getChildren().add(weaponsBranch);
        if (!cluesBranch.getChildren().isEmpty()) root.getChildren().add(cluesBranch);
        if (!valuablesBranch.getChildren().isEmpty()) root.getChildren().add(valuablesBranch);
        if (!notebooksBranch.getChildren().isEmpty()) root.getChildren().add(notebooksBranch);
        if (!othersBranch.getChildren().isEmpty()) root.getChildren().add(othersBranch);

        itemTreeView.setRoot(root);

        if (itemToSelect != null) {
            itemTreeView.getSelectionModel().select(itemToSelect);
        } else {
            TreeItem<ItemTreeItemData> firstLeaf = findFirstLeaf(root);
            if (firstLeaf != null) {
                itemTreeView.getSelectionModel().select(firstLeaf);
                newSelectedId = firstLeaf.getValue().itemId();
            }
        }

        return newSelectedId;
    }

    private static TreeItem<ItemTreeItemData> findFirstLeaf(TreeItem<ItemTreeItemData> root) {
        for (TreeItem<ItemTreeItemData> branch : root.getChildren()) {
            if (!branch.getChildren().isEmpty()) {
                return branch.getChildren().get(0);
            }
        }
        return null;
    }

    public static void renderItemDetail(VBox contentBox,
                                        SimulationRepository state,
                                        int currentStep,
                                        String selectedItemId) {
        if (contentBox == null) {
            return;
        }
        contentBox.getChildren().clear();

        SimulationStateSnapshot snap = state.getSnapshotAt(currentStep);
        ItemSnapshot itemSnap = state.getItemSnapshot(selectedItemId, currentStep);

        if (itemSnap == null) {
            Label placeholder = new Label("Select an item from the directory on the left to view its properties, history, and status.");
            placeholder.setStyle("-fx-text-fill: #94a3b8; -fx-font-style: italic; -fx-font-size: 14px; -fx-padding: 30;");
            contentBox.getChildren().add(placeholder);
            return;
        }

        Item domainItem = state.getItems().get(selectedItemId);

        VBox headerCard = buildHeaderCard(itemSnap, domainItem);
        VBox locationCard = buildLocationCard(itemSnap, snap, state);
        VBox poisonCard = buildPoisonCard(itemSnap, domainItem, snap, state);
        VBox notebookCard = buildNotebookCard(itemSnap, state);
        VBox historyCard = buildHistoryCard(itemSnap, state, currentStep);

        contentBox.getChildren().addAll(headerCard, locationCard);
        if (poisonCard != null) {
            contentBox.getChildren().add(poisonCard);
        }
        if (notebookCard != null) {
            contentBox.getChildren().add(notebookCard);
        }
        contentBox.getChildren().add(historyCard);
    }

    private static VBox buildHeaderCard(ItemSnapshot itemSnap, Item domainItem) {
        VBox headerCard = new VBox(10);
        headerCard.setPadding(new Insets(16));
        headerCard.setStyle("-fx-background-color: #ffffff; -fx-border-color: #cbd5e1; -fx-border-width: 1px; -fx-background-radius: 8px; -fx-border-radius: 8px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.04), 4, 0, 0, 1);");

        HBox titleRow = new HBox(12);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(itemSnap.name());
        nameLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");
        titleRow.getChildren().add(nameLabel);

        HBox badgesRow = new HBox(8);
        badgesRow.setAlignment(Pos.CENTER_LEFT);

        Label typeBadge;
        if (itemSnap.type() == null) {
            typeBadge = createBadge("📦 ITEM", "#475569");
        } else {
            typeBadge = switch (itemSnap.type()) {
                case WEAPON -> createBadge("🗡️ WEAPON", "#dc2626");
                case CLUE -> createBadge("🔍 CLUE", "#d97706");
                case VALUABLE -> createBadge("💎 VALUABLE", "#059669");
                case NOTEBOOK -> createBadge("📓 NOTEBOOK", "#4f46e5");
            };
        }
        badgesRow.getChildren().add(typeBadge);

        if (itemSnap.movable()) {
            badgesRow.getChildren().add(createBadge("📦 PORTABLE", "#16a34a"));
        } else {
            badgesRow.getChildren().add(createBadge("⚙️ STATIONARY FIXTURE", "#64748b"));
        }

        if (itemSnap.hidden()) {
            badgesRow.getChildren().add(createBadge("🙈 HIDDEN", "#9333ea"));
        } else {
            badgesRow.getChildren().add(createBadge("👁️ VISIBLE", "#0284c7"));
        }

        if (itemSnap.poisonConsumed()) {
            badgesRow.getChildren().add(createBadge("☠️ POISON CONSUMED", "#991b1b"));
        } else if (itemSnap.poisoned()) {
            badgesRow.getChildren().add(createBadge("☠️ POISONED", "#7e22ce"));
        }

        Label idBadge = new Label("ID: " + itemSnap.id());
        idBadge.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #64748b; -fx-font-size: 11px; -fx-padding: 3 8; -fx-background-radius: 6px;");
        badgesRow.getChildren().add(idBadge);

        headerCard.getChildren().addAll(titleRow, badgesRow);
        return headerCard;
    }

    private static VBox buildLocationCard(ItemSnapshot itemSnap, SimulationStateSnapshot snap, SimulationRepository state) {
        VBox locationCard = new VBox(12);
        locationCard.setPadding(new Insets(16));
        locationCard.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e2e8f0; -fx-border-width: 1px; -fx-background-radius: 8px; -fx-border-radius: 8px;");

        Label sectionTitle = new Label("Location & Possession");
        sectionTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #1e293b;");

        String locId = itemSnap.currentLocationId();
        String locDisplay;
        String statusDisplay;

        if (locId == null) {
            statusDisplay = "Unplaced";
            locDisplay = "None / Nowhere";
        } else if (locId.startsWith("agent_")) {
            statusDisplay = "🎒 Carried in Inventory";
            String agentName = resolveAgentName(locId, snap, state);
            locDisplay = agentName + " (" + locId + ")";
        } else {
            statusDisplay = "📍 Placed in Environment";
            String placeName = resolvePlaceName(locId, snap, state);
            locDisplay = placeName + " (" + locId + ")";
        }

        String mobilityDesc = itemSnap.movable()
                ? "Portable — can be picked up, carried, and relocated by characters."
                : "Stationary fixture — permanently affixed to the location and cannot be taken.";

        String visibilityDesc = itemSnap.hidden()
                ? "Concealed / Hidden — not immediately visible to characters in the room unless searched for."
                : "In plain sight — readily visible to any character entering the location.";

        locationCard.getChildren().addAll(
                sectionTitle,
                createProfileRow("Current Status:", statusDisplay, true),
                createProfileRow("Holder / Room:", locDisplay, false),
                createProfileRow("Mobility:", mobilityDesc, false),
                createProfileRow("Visibility:", visibilityDesc, false)
        );

        return locationCard;
    }

    private static VBox buildPoisonCard(ItemSnapshot itemSnap, Item domainItem, SimulationStateSnapshot snap, SimulationRepository state) {
        boolean hasPoison = itemSnap.poisoned() || itemSnap.poisonConsumed()
                || (domainItem != null && (domainItem.isPoisoned() || domainItem.isPoisonConsumed()));
        if (!hasPoison) {
            return null;
        }

        VBox poisonCard = new VBox(10);
        poisonCard.setPadding(new Insets(14));
        poisonCard.setStyle("-fx-background-color: #fef2f2; -fx-border-color: #f87171; -fx-border-width: 1px; -fx-background-radius: 8px; -fx-border-radius: 8px;");

        Label poisonTitle = new Label("☠️ Poison & Tampering Details");
        poisonTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #991b1b;");

        String poisonedBy = itemSnap.poisonedByAgentId() != null
                ? resolveAgentName(itemSnap.poisonedByAgentId(), snap, state) + " (" + itemSnap.poisonedByAgentId() + ")"
                : (domainItem != null && domainItem.getPoisonedByAgentId() != null
                ? resolveAgentName(domainItem.getPoisonedByAgentId(), snap, state) : "Unknown Suspect");

        String targetAgent = itemSnap.poisonTargetAgentId() != null
                ? resolveAgentName(itemSnap.poisonTargetAgentId(), snap, state) + " (" + itemSnap.poisonTargetAgentId() + ")"
                : (domainItem != null && domainItem.getPoisonTargetAgentId() != null
                ? resolveAgentName(domainItem.getPoisonTargetAgentId(), snap, state) : "Unspecified");

        String status = itemSnap.poisonConsumed()
                ? "⚠️ LETHAL DOSE CONSUMED by victim."
                : "🧪 Active lethal dose present on item.";

        poisonCard.getChildren().addAll(
                poisonTitle,
                createProfileRow("Poison Status:", status, true),
                createProfileRow("Poisoned By:", poisonedBy, false),
                createProfileRow("Intended Target:", targetAgent, false)
        );

        if (domainItem != null && domainItem.getPoisonDetails() != null && !domainItem.getPoisonDetails().isBlank()) {
            Label detailsLabel = new Label("Details: " + domainItem.getPoisonDetails());
            detailsLabel.setWrapText(true);
            detailsLabel.setStyle("-fx-font-style: italic; -fx-font-size: 12px; -fx-text-fill: #7f1d1d;");
            poisonCard.getChildren().add(detailsLabel);
        }

        return poisonCard;
    }

    private static VBox buildNotebookCard(ItemSnapshot itemSnap, SimulationRepository state) {
        List<KnowledgeSnapshot> kbList = itemSnap.notebookKnowledge();
        if (kbList == null || kbList.isEmpty()) {
            return null;
        }

        VBox nbCard = new VBox(10);
        nbCard.setPadding(new Insets(16));
        nbCard.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e2e8f0; -fx-border-width: 1px; -fx-background-radius: 8px; -fx-border-radius: 8px;");

        Label sectionTitle = new Label("📓 Recorded Notes & Clues (" + kbList.size() + " entries)");
        sectionTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #1e293b;");
        nbCard.getChildren().add(sectionTitle);

        VBox itemsBox = new VBox(6);
        for (KnowledgeSnapshot k : kbList) {
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(6, 10, 6, 10));

            String timeStr = state.getClockTime(k.timeStep());
            Label stepBadge = new Label("Step " + k.timeStep() + " (" + timeStr + ")");
            stepBadge.setStyle("-fx-background-color: #e2e8f0; -fx-text-fill: #334155; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 2 6; -fx-background-radius: 4px;");

            Label infoLabel = new Label(k.information());
            infoLabel.setWrapText(true);
            infoLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #1e293b;");
            HBox.setHgrow(infoLabel, Priority.ALWAYS);

            row.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-width: 1px; -fx-background-radius: 4px; -fx-border-radius: 4px;");
            row.getChildren().addAll(stepBadge, infoLabel);
            itemsBox.getChildren().add(row);
        }
        nbCard.getChildren().add(itemsBox);

        return nbCard;
    }

    private static VBox buildHistoryCard(ItemSnapshot currentItemSnap, SimulationRepository state, int currentStep) {
        VBox historyCard = new VBox(10);
        historyCard.setPadding(new Insets(16));
        historyCard.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e2e8f0; -fx-border-width: 1px; -fx-background-radius: 8px; -fx-border-radius: 8px;");

        String itemId = currentItemSnap.id();
        List<com.murderhecoded.domain.TimeLocationEvent> itemEvents = state.getEventsForItem(itemId, currentStep);
        int evCount = itemEvents != null ? itemEvents.size() : 0;

        Label historyTitle = new Label("🔗 Chain of Custody & Activity Log (" + evCount + " events)");
        historyTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #1e293b;");
        historyCard.getChildren().add(historyTitle);

        VBox timelineBox = new VBox(8);

        // First add initial placement at step 0 if available
        SimulationStateSnapshot step0Snap = state.getSnapshotAt(0);
        if (step0Snap != null && step0Snap.items() != null && step0Snap.items().containsKey(itemId)) {
            ItemSnapshot initSnap = step0Snap.items().get(itemId);
            String initLocDesc;
            if (initSnap.currentLocationId() == null) {
                initLocDesc = "Unplaced";
            } else if (initSnap.currentLocationId().startsWith("agent_")) {
                initLocDesc = "In possession of " + resolveAgentName(initSnap.currentLocationId(), step0Snap, state);
            } else {
                initLocDesc = "Located in " + resolvePlaceName(initSnap.currentLocationId(), step0Snap, state);
            }
            HBox initRow = new HBox(8);
            initRow.setAlignment(Pos.CENTER_LEFT);
            initRow.setPadding(new Insets(6, 10, 6, 10));
            initRow.setStyle("-fx-background-color: #f1f5f9; -fx-border-color: #cbd5e1; -fx-border-width: 1px; -fx-background-radius: 6px;");

            Label stepBadge = new Label("Step 0 (" + state.getClockTime(0) + ")");
            stepBadge.setStyle("-fx-background-color: #e2e8f0; -fx-text-fill: #334155; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 2 6; -fx-background-radius: 4px;");

            Label badge = new Label("📍 INITIAL POSITION");
            badge.setStyle("-fx-background-color: #0284c7; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 10px; -fx-padding: 2 6; -fx-background-radius: 4px;");

            Label desc = new Label(initLocDesc + (initSnap.hidden() ? " [Hidden]" : "") + (initSnap.poisoned() ? " [Poisoned]" : ""));
            desc.setStyle("-fx-font-size: 12px; -fx-text-fill: #334155;");
            initRow.getChildren().addAll(stepBadge, badge, desc);
            timelineBox.getChildren().add(initRow);
        }

        if (itemEvents != null && !itemEvents.isEmpty()) {
            for (com.murderhecoded.domain.TimeLocationEvent ev : itemEvents) {
                if (ev.getActions() == null) continue;
                for (com.murderhecoded.domain.Action a : ev.getActions()) {
                    boolean mentionsItem = itemId.equals(a.getTargetItemId()) || itemId.equals(a.getWeaponItemId());
                    if (!mentionsItem) continue;

                    VBox evBox = new VBox(4);
                    evBox.setPadding(new Insets(6, 10, 6, 10));
                    evBox.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #cbd5e1; -fx-border-width: 1px; -fx-background-radius: 6px;");

                    HBox topRow = new HBox(8);
                    topRow.setAlignment(Pos.CENTER_LEFT);

                    String timeStr = state.getClockTime(ev.getTimeStep());
                    Label stepBadge = new Label("Step " + ev.getTimeStep() + " (" + timeStr + ")");
                    stepBadge.setStyle("-fx-background-color: #e2e8f0; -fx-text-fill: #334155; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 2 6; -fx-background-radius: 4px;");

                    String actorName = state.resolveAgentName(a.getActorId(), ev.getTimeStep());
                    Label actionBadge = createActionBadge(a.getActionType());

                    StringBuilder sb = new StringBuilder();
                    if (a.getActionType() == com.murderhecoded.domain.ActionType.MURDER) {
                        sb.append(actorName).append(" used as weapon in crime against ")
                          .append(state.resolveAgentName(a.getTargetAgentId(), ev.getTimeStep()));
                    } else if (a.getActionType() == com.murderhecoded.domain.ActionType.MANIPULATE_ITEM) {
                        String inter = a.getInteraction() != null ? a.getInteraction().toUpperCase() : "MANIPULATED";
                        sb.append(actorName).append(" [").append(inter).append("]");
                        if (a.getDetails() != null && !a.getDetails().isBlank()) {
                            sb.append(" - ").append(a.getDetails());
                        }
                    } else {
                        sb.append(actorName).append(" interacted with item (").append(a.getActionType().name()).append(")");
                        if (a.getDetails() != null && !a.getDetails().isBlank()) {
                            sb.append(" - ").append(a.getDetails());
                        }
                    }

                    Label desc = new Label(sb.toString());
                    desc.setWrapText(true);
                    desc.setStyle("-fx-font-size: 12px; -fx-text-fill: #334155;");
                    HBox.setHgrow(desc, Priority.ALWAYS);

                    topRow.getChildren().addAll(stepBadge, actionBadge, desc);
                    evBox.getChildren().add(topRow);
                    timelineBox.getChildren().add(evBox);
                }
            }
        }

        if (timelineBox.getChildren().isEmpty()) {
            Label noHistory = new Label("No movement or actions recorded for this item up to Step " + currentStep + ".");
            noHistory.setStyle("-fx-text-fill: #94a3b8; -fx-font-style: italic; -fx-font-size: 13px;");
            historyCard.getChildren().add(noHistory);
        } else {
            historyCard.getChildren().add(timelineBox);
        }

        return historyCard;
    }

    private static Label createActionBadge(com.murderhecoded.domain.ActionType type) {
        if (type == null) type = com.murderhecoded.domain.ActionType.OBSERVE;
        String iconText;
        String color;
        switch (type) {
            case MURDER -> { iconText = "💀 MURDER"; color = "#dc2626"; }
            case ACCUSE -> { iconText = "⚖️ ACCUSE"; color = "#ea580c"; }
            case MOVE -> { iconText = "🚶 MOVE"; color = "#2563eb"; }
            case TALK -> { iconText = "💬 TALK"; color = "#059669"; }
            case SEARCH -> { iconText = "🔍 SEARCH"; color = "#7c3aed"; }
            case MANIPULATE_ITEM -> { iconText = "📦 ITEM"; color = "#d97706"; }
            case REFLECT -> { iconText = "🧠 REFLECT"; color = "#9333ea"; }
            case ALTER_ENVIRONMENT -> { iconText = "🛠️ TRACE"; color = "#0d9488"; }
            default -> { iconText = "👁️ OBSERVE"; color = "#475569"; }
        }
        Label badge = new Label(iconText);
        badge.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 10px; -fx-padding: 2 6; -fx-background-radius: 4px;");
        return badge;
    }

    private static Label createBadge(String text, String bgColor) {
        Label badge = new Label(text);
        badge.setStyle("-fx-background-color: " + bgColor + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 3 10; -fx-background-radius: 12px;");
        return badge;
    }

    private static HBox createProfileRow(String label, String value, boolean isBoldVal) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569; -fx-font-size: 13px; -fx-min-width: 140px;");
        Label val = new Label(value);
        val.setWrapText(true);
        val.setStyle("-fx-text-fill: #0f172a; -fx-font-size: 13px;" + (isBoldVal ? " -fx-font-weight: bold;" : ""));
        HBox.setHgrow(val, Priority.ALWAYS);
        row.getChildren().addAll(lbl, val);
        return row;
    }

    private static String resolveAgentName(String agentId, SimulationStateSnapshot snap, SimulationRepository state) {
        if (snap != null && snap.agents() != null && snap.agents().containsKey(agentId)) {
            return snap.agents().get(agentId).name();
        }
        if (state.getAgents().containsKey(agentId)) {
            return state.getAgents().get(agentId).getName();
        }
        return agentId;
    }

    private static String resolvePlaceName(String placeId, SimulationStateSnapshot snap, SimulationRepository state) {
        if (snap != null && snap.places() != null && snap.places().containsKey(placeId)) {
            return snap.places().get(placeId).name();
        }
        if (state.getLocations().containsKey(placeId)) {
            return state.getLocations().get(placeId).getName();
        }
        return placeId;
    }
}
