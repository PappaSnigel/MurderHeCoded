package com.murderhecoded.ui;

import com.murderhecoded.domain.*;
import com.murderhecoded.repository.SimulationRepository;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

@SuppressWarnings("PMD")
public class ItemsTabPresenter {

    public static void renderItems(FlowPane itemsFlowPane,
                                   Label itemsHeaderLabel,
                                   SimulationRepository state,
                                   int currentStep,
                                   String typeFilter,
                                   String locFilter,
                                   String searchText) {
        if (itemsFlowPane == null) {
            return;
        }
        itemsFlowPane.getChildren().clear();

        SimulationStateSnapshot snap = state.getSnapshotAt(currentStep);

        Collection<ItemSnapshot> itemsToDisplay = snap.items().values();
        if (itemsToDisplay.isEmpty()) {
            Label emptyLabel = new Label("No items generated yet.");
            emptyLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-style: italic; -fx-font-size: 14px; -fx-padding: 20;");
            itemsFlowPane.getChildren().add(emptyLabel);
            if (itemsHeaderLabel != null) {
                itemsHeaderLabel.setText("Story Items (0)");
            }
            return;
        }

        String searchLower = searchText != null ? searchText.trim().toLowerCase() : "";
        int count = 0;

        for (ItemSnapshot item : itemsToDisplay) {
            if (typeFilter != null && !"All Types".equals(typeFilter) && (item.type() == null || !item.type().name().equalsIgnoreCase(typeFilter))) {
                continue;
            }

            boolean isCarried = snap.agents().containsKey(item.currentLocationId());
            boolean isInRoom = snap.locations().containsKey(item.currentLocationId());

            String holderName = resolveHolderName(item, isCarried, isInRoom, snap.agents(), snap.locations(), state, currentStep);

            if (locFilter != null && !"All Locations".equals(locFilter)) {
                if ("Carried by Characters".equals(locFilter) && !isCarried) {
                    continue;
                }
                if ("In Rooms".equals(locFilter) && !isInRoom) {
                    continue;
                }
                if ("Hidden Only".equals(locFilter) && !item.hidden()) {
                    continue;
                }
            }

            if (!searchLower.isEmpty()) {
                boolean matchName = item.name() != null && item.name().toLowerCase().contains(searchLower);
                boolean matchLoc = holderName.toLowerCase().contains(searchLower);
                if (!matchName && !matchLoc) {
                    continue;
                }
            }

            count++;
            itemsFlowPane.getChildren().add(createItemCard(item, isCarried, isInRoom, holderName));
        }

        if (itemsHeaderLabel != null) {
            itemsHeaderLabel.setText("Story Items (" + count + ")");
        }
    }

    private static String resolveHolderName(ItemSnapshot item,
                                             boolean isCarried,
                                             boolean isInRoom,
                                             Map<String, com.murderhecoded.domain.AgentSnapshot> agentSnapMap,
                                             Map<String, LocationSnapshot> placeSnapMap,
                                             SimulationRepository state,
                                             int currentStep) {
        String locId = item.currentLocationId();
        if (locId == null) {
            return "None (Unplaced)";
        }
        if (isCarried && agentSnapMap.containsKey(locId)) {
            return agentSnapMap.get(locId).name();
        }
        if (isInRoom && placeSnapMap.containsKey(locId)) {
            return placeSnapMap.get(locId).name();
        }
        return state.resolveLocationName(locId, currentStep);
    }

    private static Node createItemCard(ItemSnapshot item, boolean isCarried, boolean isInRoom, String holderName) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(12));
        card.setPrefWidth(270);

        String borderColor = "#cbd5e1";
        String bgColor = "#ffffff";
        String badgeBg = "#64748b";
        String typeName = item.type() != null ? item.type().name() : "ITEM";

        if (item.type() == ItemType.WEAPON) {
            borderColor = "#ef4444";
            bgColor = "#fef2f2";
            badgeBg = "#dc2626";
        } else if (item.type() == ItemType.CLUE) {
            borderColor = "#3b82f6";
            bgColor = "#eff6ff";
            badgeBg = "#2563eb";
        } else if (item.type() == ItemType.VALUABLE) {
            borderColor = "#f59e0b";
            bgColor = "#fffbeb";
            badgeBg = "#d97706";
        } else if (item.type() == ItemType.NOTEBOOK) {
            borderColor = "#8b5cf6";
            bgColor = "#f5f3ff";
            badgeBg = "#7c3aed";
        }

        card.setStyle("-fx-border-color: " + borderColor + "; -fx-border-width: 2px; -fx-background-color: " + bgColor +
                      "; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.04), 4, 0, 0, 1);");

        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);
        Label nameLabel = new Label(item.name());
        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #0f172a;");
        nameLabel.setWrapText(true);
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        Label typeBadge = new Label(typeName);
        typeBadge.setStyle("-fx-background-color: " + badgeBg + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 10px; -fx-padding: 2 6; -fx-background-radius: 10px;");

        top.getChildren().addAll(nameLabel, typeBadge);

        Label locLabel = new Label();
        if (isCarried) {
            locLabel.setText("👤 Carried by: " + holderName);
            locLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #1d4ed8;");
        } else if (isInRoom) {
            if (!item.movable()) {
                locLabel.setText("⚙️ Fixed in Room: " + holderName);
                locLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;");
            } else {
                locLabel.setText("📍 In Room: " + holderName);
                locLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #334155;");
            }
        } else {
            locLabel.setText("📍 Location: " + holderName);
            locLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");
        }
        locLabel.setWrapText(true);

        Label statusBadge;
        if (item.hidden()) {
            statusBadge = new Label("⚠️ Hidden");
            statusBadge.setStyle("-fx-font-size: 11px; -fx-text-fill: #b45309; -fx-font-weight: bold;");
        } else if (item.poisoned()) {
            statusBadge = new Label("☠️ Laced with Poison");
            statusBadge.setStyle("-fx-font-size: 11px; -fx-text-fill: #9333ea; -fx-font-weight: bold;");
        } else if (item.poisonConsumed()) {
            statusBadge = new Label("☠️ Poison Expended");
            statusBadge.setStyle("-fx-font-size: 11px; -fx-text-fill: #6b21a8; -fx-font-weight: bold;");
        } else if (!item.movable()) {
            statusBadge = new Label("⚙️ Stationary Machine/Fixture");
            statusBadge.setStyle("-fx-font-size: 11px; -fx-text-fill: #475569; -fx-font-weight: bold;");
        } else {
            statusBadge = new Label("👁 Portable");
            statusBadge.setStyle("-fx-font-size: 11px; -fx-text-fill: #15803d;");
        }

        card.getChildren().addAll(top, locLabel, statusBadge);

        if (item.notebookKnowledge() != null && !item.notebookKnowledge().isEmpty()) {
            VBox notesBox = new VBox(3);
            notesBox.setStyle("-fx-background-color: rgba(255,255,255,0.7); -fx-padding: 6; -fx-background-radius: 4; -fx-border-color: #ddd; -fx-border-radius: 4;");
            Label notesTitle = new Label("Recorded Notes:");
            notesTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: #5b21b6;");
            notesBox.getChildren().add(notesTitle);
            for (KnowledgeSnapshot kn : item.notebookKnowledge()) {
                Label noteLine = new Label("• [Step " + kn.timeStep() + "] " + kn.information());
                noteLine.setWrapText(true);
                noteLine.setStyle("-fx-font-size: 10px; -fx-text-fill: #374151;");
                notesBox.getChildren().add(noteLine);
            }
            card.getChildren().add(notesBox);
        }

        return card;
    }
}
