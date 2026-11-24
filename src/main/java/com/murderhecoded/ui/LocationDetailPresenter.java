package com.murderhecoded.ui;

import com.murderhecoded.domain.Agent;
import com.murderhecoded.domain.AgentSnapshot;
import com.murderhecoded.domain.EnvironmentTraceSnapshot;
import com.murderhecoded.domain.Item;
import com.murderhecoded.domain.ItemSnapshot;
import com.murderhecoded.domain.ItemType;
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
public class LocationDetailPresenter {

    public record LocationTreeItemData(String label, String locationId, boolean isCategory) {
        @Override
        public String toString() {
            return label;
        }
    }

    public static String updateLocationTreeView(TreeView<LocationTreeItemData> locationTreeView,
                                                SimulationRepository state,
                                                int currentStep,
                                                String selectedLocationId) {
        if (locationTreeView == null) {
            return selectedLocationId;
        }

        SimulationStateSnapshot snap = state.getSnapshotAt(currentStep);

        TreeItem<LocationTreeItemData> root = new TreeItem<>(new LocationTreeItemData("Locations", null, true));
        root.setExpanded(true);

        TreeItem<LocationTreeItemData> itemToSelect = null;
        String newSelectedId = selectedLocationId;

        for (LocationSnapshot loc : snap.locations().values()) {
            String locId = loc.id();

            long peopleCount = snap.agents().values().stream()
                    .filter(a -> a.hasArrived() && a.isAlive() && locId.equals(a.currentLocationId()))
                    .count();

            long itemsCount = snap.items().values().stream()
                    .filter(i -> locId.equals(i.currentLocationId()))
                    .count();

            String countSuffix = String.format(" [👥 %d | 📦 %d]", peopleCount, itemsCount);
            String label = loc.name() + countSuffix;

            TreeItem<LocationTreeItemData> leaf = new TreeItem<>(new LocationTreeItemData(label, loc.id(), false));
            root.getChildren().add(leaf);

            if (loc.id().equals(newSelectedId)) {
                itemToSelect = leaf;
            }
        }

        locationTreeView.setRoot(root);

        if (itemToSelect != null) {
            locationTreeView.getSelectionModel().select(itemToSelect);
        } else if (!root.getChildren().isEmpty()) {
            TreeItem<LocationTreeItemData> firstLeaf = root.getChildren().get(0);
            locationTreeView.getSelectionModel().select(firstLeaf);
            newSelectedId = firstLeaf.getValue().locationId();
        }

        return newSelectedId;
    }

    public static void renderLocationDetail(VBox contentBox,
                                            SimulationRepository state,
                                            int currentStep,
                                            String selectedLocationId) {
        if (contentBox == null) {
            return;
        }
        contentBox.getChildren().clear();

        SimulationStateSnapshot snap = state.getSnapshotAt(currentStep);
        LocationSnapshot locSnap = state.getLocationSnapshot(selectedLocationId, currentStep);

        if (locSnap == null) {
            Label placeholder = new Label("Select a location from the directory on the left to view present characters, items, and environmental traces.");
            placeholder.setStyle("-fx-text-fill: #94a3b8; -fx-font-style: italic; -fx-font-size: 14px; -fx-padding: 30;");
            contentBox.getChildren().add(placeholder);
            return;
        }

        Location domainLoc = state.getLocations().get(selectedLocationId);

        VBox headerCard = buildHeaderCard(locSnap, snap, state);
        VBox charactersCard = buildCharactersPresentCard(locSnap, snap, state);
        VBox itemsCard = buildItemsPresentCard(locSnap, snap, state);
        VBox tracesCard = buildTracesCard(locSnap, state);
        VBox historyCard = buildHistoryCard(locSnap, state, currentStep);

        contentBox.getChildren().addAll(headerCard, charactersCard, itemsCard);
        if (tracesCard != null) {
            contentBox.getChildren().add(tracesCard);
        }
        contentBox.getChildren().add(historyCard);
    }

    private static VBox buildHeaderCard(LocationSnapshot locSnap, SimulationStateSnapshot snap, SimulationRepository state) {
        VBox headerCard = new VBox(12);
        headerCard.setPadding(new Insets(16));
        headerCard.setStyle("-fx-background-color: #ffffff; -fx-border-color: #cbd5e1; -fx-border-width: 1px; -fx-background-radius: 8px; -fx-border-radius: 8px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.04), 4, 0, 0, 1);");

        HBox titleRow = new HBox(12);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label("📍 " + locSnap.name());
        nameLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");
        titleRow.getChildren().add(nameLabel);

        HBox badgesRow = new HBox(8);
        badgesRow.setAlignment(Pos.CENTER_LEFT);

        Label idBadge = new Label("ID: " + locSnap.id());
        idBadge.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #64748b; -fx-font-size: 11px; -fx-padding: 3 8; -fx-background-radius: 6px;");
        badgesRow.getChildren().add(idBadge);

        long peopleCount = 0;
        if (snap != null && snap.agents() != null) {
            peopleCount = snap.agents().values().stream()
                    .filter(a -> a.hasArrived() && a.isAlive() && locSnap.id().equals(a.currentLocationId()))
                    .count();
        } else if (!state.getAgents().isEmpty()) {
            peopleCount = state.getAgents().values().stream()
                    .filter(a -> a.isHasArrived() && a.isAlive() && locSnap.id().equals(a.getCurrentLocationId()))
                    .count();
        }

        if (peopleCount > 0) {
            badgesRow.getChildren().add(createBadge("👥 " + peopleCount + " PRESENT", "#2563eb"));
        } else {
            badgesRow.getChildren().add(createBadge("👥 NOBODY PRESENT", "#64748b"));
        }

        long itemsCount = 0;
        if (snap != null && snap.items() != null) {
            itemsCount = snap.items().values().stream()
                    .filter(i -> locSnap.id().equals(i.currentLocationId()))
                    .count();
        } else if (!state.getItems().isEmpty()) {
            itemsCount = state.getItems().values().stream()
                    .filter(i -> locSnap.id().equals(i.getCurrentLocationId()))
                    .count();
        }

        if (itemsCount > 0) {
            badgesRow.getChildren().add(createBadge("📦 " + itemsCount + " ITEMS", "#059669"));
        } else {
            badgesRow.getChildren().add(createBadge("📦 NO ITEMS", "#64748b"));
        }

        int traceCount = locSnap.traces() != null ? locSnap.traces().size() : 0;
        if (traceCount > 0) {
            badgesRow.getChildren().add(createBadge("🔎 " + traceCount + " TRACES", "#d97706"));
        }

        headerCard.getChildren().addAll(titleRow, badgesRow);

        if (locSnap.description() != null && !locSnap.description().isBlank()) {
            VBox descBox = new VBox(4);
            descBox.setPadding(new Insets(10, 14, 10, 14));
            descBox.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-width: 1px; -fx-background-radius: 6px; -fx-border-radius: 6px;");

            Label descTitle = new Label("Atmosphere & Setting Description:");
            descTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #475569;");

            Label descText = new Label(locSnap.description());
            descText.setWrapText(true);
            descText.setStyle("-fx-font-size: 13px; -fx-text-fill: #334155; -fx-line-spacing: 2;");

            descBox.getChildren().addAll(descTitle, descText);
            headerCard.getChildren().add(descBox);
        }

        return headerCard;
    }

    private static VBox buildCharactersPresentCard(LocationSnapshot locSnap, SimulationStateSnapshot snap, SimulationRepository state) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(16));
        card.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e2e8f0; -fx-border-width: 1px; -fx-background-radius: 8px; -fx-border-radius: 8px;");

        List<AgentSnapshot> presentAgents = state.getPresentAgentsInLocation(locSnap.id(), snap.timeStep());

        Label title = new Label("👥 Characters Currently in Room (" + presentAgents.size() + ")");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #1e293b;");
        card.getChildren().add(title);

        if (presentAgents.isEmpty()) {
            Label empty = new Label("No characters are currently in this location at this time step.");
            empty.setStyle("-fx-text-fill: #94a3b8; -fx-font-style: italic; -fx-font-size: 13px;");
            card.getChildren().add(empty);
        } else {
            VBox agentsList = new VBox(8);
            for (AgentSnapshot a : presentAgents) {
                VBox aBox = new VBox(6);
                aBox.setPadding(new Insets(8, 12, 8, 12));
                aBox.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-width: 1px; -fx-background-radius: 6px; -fx-border-radius: 6px;");

                HBox topRow = new HBox(8);
                topRow.setAlignment(Pos.CENTER_LEFT);

                Label nameLabel = new Label(a.name());
                nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #0f172a;");
                topRow.getChildren().add(nameLabel);

                if (a.isDetective()) {
                    topRow.getChildren().add(createBadge("🕵️ DETECTIVE", "#2563eb"));
                } else if (a.isMurderer()) {
                    topRow.getChildren().add(createBadge("🔪 MURDERER", "#dc2626"));
                } else {
                    String role = a.role() != null ? a.role().toUpperCase() : "GUEST";
                    topRow.getChildren().add(createBadge("👤 " + role, "#475569"));
                }

                if (!a.isAlive()) {
                    topRow.getChildren().add(createBadge("💀 DECEASED", "#475569"));
                } else if (!a.hasArrived()) {
                    topRow.getChildren().add(createBadge("⏳ EN ROUTE", "#d97706"));
                } else {
                    topRow.getChildren().add(createBadge("💚 ALIVE", "#16a34a"));
                }

                // Carried items
                List<ItemSnapshot> carriedItems = state.getCarriedItems(a.id(), snap.timeStep());
                String carriedStr = carriedItems.isEmpty() ? "None" : carriedItems.stream().map(ItemSnapshot::name).collect(java.util.stream.Collectors.joining(", "));
                Label carriedLabel = new Label("🎒 Carrying: " + carriedStr);
                carriedLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569;");
                carriedLabel.setWrapText(true);

                aBox.getChildren().addAll(topRow, carriedLabel);
                agentsList.getChildren().add(aBox);
            }
            card.getChildren().add(agentsList);
        }

        return card;
    }

    private static VBox buildItemsPresentCard(LocationSnapshot locSnap, SimulationStateSnapshot snap, SimulationRepository state) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(16));
        card.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e2e8f0; -fx-border-width: 1px; -fx-background-radius: 8px; -fx-border-radius: 8px;");

        List<ItemSnapshot> presentItems = state.getItemsInLocation(locSnap.id(), snap.timeStep());

        Label title = new Label("📦 Items & Objects Located Here (" + presentItems.size() + ")");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #1e293b;");
        card.getChildren().add(title);

        if (presentItems.isEmpty()) {
            Label empty = new Label("No items or clues are currently placed in this room.");
            empty.setStyle("-fx-text-fill: #94a3b8; -fx-font-style: italic; -fx-font-size: 13px;");
            card.getChildren().add(empty);
        } else {
            VBox itemsList = new VBox(6);
            for (ItemSnapshot itm : presentItems) {
                HBox row = new HBox(8);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(6, 10, 6, 10));
                row.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-width: 1px; -fx-background-radius: 6px; -fx-border-radius: 6px;");

                Label nameLabel = new Label(itm.name());
                nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #0f172a;");
                row.getChildren().add(nameLabel);

                Label typeBadge;
                if (itm.type() == null) {
                    typeBadge = createBadge("📦 ITEM", "#475569");
                } else {
                    typeBadge = switch (itm.type()) {
                        case WEAPON -> createBadge("🗡️ WEAPON", "#dc2626");
                        case CLUE -> createBadge("🔍 CLUE", "#d97706");
                        case VALUABLE -> createBadge("💎 VALUABLE", "#059669");
                        case NOTEBOOK -> createBadge("📓 NOTEBOOK", "#4f46e5");
                    };
                }
                row.getChildren().add(typeBadge);

                if (!itm.movable()) {
                    row.getChildren().add(createBadge("⚙️ STATIONARY", "#64748b"));
                }
                if (itm.hidden()) {
                    row.getChildren().add(createBadge("🙈 HIDDEN", "#9333ea"));
                }
                if (itm.poisonConsumed()) {
                    row.getChildren().add(createBadge("☠️ CONSUMED", "#991b1b"));
                } else if (itm.poisoned()) {
                    row.getChildren().add(createBadge("☠️ POISONED", "#7e22ce"));
                }

                itemsList.getChildren().add(row);
            }
            card.getChildren().add(itemsList);
        }

        return card;
    }

    private static VBox buildTracesCard(LocationSnapshot locSnap, SimulationRepository state) {
        List<EnvironmentTraceSnapshot> traces = locSnap.traces();
        if (traces == null || traces.isEmpty()) {
            return null;
        }

        VBox card = new VBox(10);
        card.setPadding(new Insets(16));
        card.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e2e8f0; -fx-border-width: 1px; -fx-background-radius: 8px; -fx-border-radius: 8px;");

        Label title = new Label("🔎 Environmental Traces & Scene Evidence (" + traces.size() + ")");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #1e293b;");
        card.getChildren().add(title);

        VBox tracesList = new VBox(6);
        for (EnvironmentTraceSnapshot tr : traces) {
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(6, 10, 6, 10));
            row.setStyle("-fx-background-color: #fefce8; -fx-border-color: #fde68a; -fx-border-width: 1px; -fx-background-radius: 6px; -fx-border-radius: 6px;");

            String timeStr = state.getClockTime(tr.timeStepCreated());
            Label stepBadge = new Label("Step " + tr.timeStepCreated() + " (" + timeStr + ")");
            stepBadge.setStyle("-fx-background-color: #fef08a; -fx-text-fill: #854d0e; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 2 6; -fx-background-radius: 4px;");

            String creatorName = tr.creatorId() != null ? resolveAgentName(tr.creatorId(), state) : "Unknown";
            Label creatorBadge = new Label("By " + creatorName);
            creatorBadge.setStyle("-fx-background-color: #e2e8f0; -fx-text-fill: #334155; -fx-font-size: 11px; -fx-padding: 2 6; -fx-background-radius: 4px;");

            Label descLabel = new Label(tr.description());
            descLabel.setWrapText(true);
            descLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #78350f;");
            HBox.setHgrow(descLabel, Priority.ALWAYS);

            row.getChildren().addAll(stepBadge, creatorBadge, descLabel);
            tracesList.getChildren().add(row);
        }
        card.getChildren().add(tracesList);

        return card;
    }

    private static VBox buildHistoryCard(LocationSnapshot locSnap, SimulationRepository state, int currentStep) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(16));
        card.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e2e8f0; -fx-border-width: 1px; -fx-background-radius: 8px; -fx-border-radius: 8px;");

        List<com.murderhecoded.domain.TimeLocationEvent> events = state.getEventsForLocation(locSnap.id(), currentStep);
        int evCount = events != null ? events.size() : 0;

        Label title = new Label("🕒 Activity Log & History (" + evCount + " events in this location)");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #1e293b;");
        card.getChildren().add(title);

        if (events == null || events.isEmpty()) {
            Label noHistory = new Label("No specific events or movements recorded in this location up to Step " + currentStep + ".");
            noHistory.setStyle("-fx-text-fill: #94a3b8; -fx-font-style: italic; -fx-font-size: 13px;");
            card.getChildren().add(noHistory);
            return card;
        }

        VBox historyList = new VBox(8);
        for (com.murderhecoded.domain.TimeLocationEvent ev : events) {
            VBox evBox = new VBox(4);
            evBox.setPadding(new Insets(8, 10, 8, 10));
            evBox.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #cbd5e1; -fx-border-width: 1px; -fx-background-radius: 6px; -fx-border-radius: 6px;");

            HBox topRow = new HBox(8);
            topRow.setAlignment(Pos.CENTER_LEFT);

            String timeStr = state.getClockTime(ev.getTimeStep());
            Label stepBadge = new Label("Step " + ev.getTimeStep() + " (" + timeStr + ")");
            stepBadge.setStyle("-fx-background-color: #e2e8f0; -fx-text-fill: #334155; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 2 6; -fx-background-radius: 4px;");

            String peopleStr = ev.getPresentAgentNames() != null && !ev.getPresentAgentNames().isEmpty()
                    ? "👥 Present: " + String.join(", ", ev.getPresentAgentNames())
                    : "👥 Present: Nobody";
            Label peopleLabel = new Label(peopleStr);
            peopleLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569; -fx-font-weight: bold;");

            topRow.getChildren().addAll(stepBadge, peopleLabel);
            evBox.getChildren().add(topRow);

            if (ev.getActions() != null && !ev.getActions().isEmpty()) {
                for (com.murderhecoded.domain.Action a : ev.getActions()) {
                    HBox actionRow = new HBox(6);
                    actionRow.setAlignment(Pos.CENTER_LEFT);

                    Label typeBadge = createActionBadge(a.getActionType());
                    actionRow.getChildren().add(typeBadge);

                    String actorName = state.resolveAgentName(a.getActorId(), ev.getTimeStep());
                    StringBuilder sb = new StringBuilder(actorName).append(": ").append(a.getActionType().name());

                    if (a.getTargetAgentId() != null) {
                        sb.append(" -> ").append(state.resolveAgentName(a.getTargetAgentId(), ev.getTimeStep()));
                    }
                    if (a.getTargetLocationId() != null) {
                        sb.append(" -> Moved to ").append(state.resolveLocationName(a.getTargetLocationId(), ev.getTimeStep()));
                    }
                    if (a.getTargetItemId() != null) {
                        com.murderhecoded.domain.ItemSnapshot it = state.getItemSnapshot(a.getTargetItemId(), ev.getTimeStep());
                        sb.append(" (Item: ").append(it != null ? it.name() : a.getTargetItemId()).append(")");
                    }
                    if (a.getDetails() != null && !a.getDetails().isBlank()) {
                        sb.append(" - ").append(a.getDetails());
                    }

                    Label descLabel = new Label(sb.toString());
                    descLabel.setWrapText(true);
                    descLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #334155;");
                    HBox.setHgrow(descLabel, Priority.ALWAYS);

                    actionRow.getChildren().add(descLabel);
                    evBox.getChildren().add(actionRow);
                }
            }
            historyList.getChildren().add(evBox);
        }

        card.getChildren().add(historyList);
        return card;
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

    private static String resolveAgentName(String agentId, SimulationRepository state) {
        if (state.getAgents().containsKey(agentId)) {
            return state.getAgents().get(agentId).getName();
        }
        return agentId;
    }
}
