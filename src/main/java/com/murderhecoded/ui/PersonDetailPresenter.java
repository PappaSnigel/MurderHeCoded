package com.murderhecoded.ui;

import com.murderhecoded.domain.Agent;
import com.murderhecoded.domain.AgentSnapshot;
import com.murderhecoded.domain.Item;
import com.murderhecoded.domain.ItemSnapshot;
import com.murderhecoded.domain.KnowledgeSnapshot;
import com.murderhecoded.domain.MemorySnapshot;
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
import java.util.List;

@SuppressWarnings("PMD")
public class PersonDetailPresenter {

    public record PersonTreeItemData(String label, String agentId, boolean isCategory) {
        @Override
        public String toString() {
            return label;
        }
    }

    public static String updatePersonTreeView(TreeView<PersonTreeItemData> personTreeView,
                                              SimulationRepository state,
                                              int currentStep,
                                              String selectedPersonId) {
        if (personTreeView == null) {
            return selectedPersonId;
        }

        SimulationStateSnapshot snap = state.getSnapshotAt(currentStep);

        TreeItem<PersonTreeItemData> root = new TreeItem<>(new PersonTreeItemData("Characters", null, true));
        root.setExpanded(true);

        TreeItem<PersonTreeItemData> detectiveBranch = new TreeItem<>(new PersonTreeItemData("🕵️ Detective", null, true));
        detectiveBranch.setExpanded(true);

        TreeItem<PersonTreeItemData> suspectsBranch = new TreeItem<>(new PersonTreeItemData("👥 Suspects & Guests", null, true));
        suspectsBranch.setExpanded(true);

        TreeItem<PersonTreeItemData> deceasedBranch = new TreeItem<>(new PersonTreeItemData("💀 Deceased", null, true));
        deceasedBranch.setExpanded(true);

        TreeItem<PersonTreeItemData> itemToSelect = null;
        String newSelectedId = selectedPersonId;

        for (AgentSnapshot a : snap.agents().values()) {
            StringBuilder label = new StringBuilder(a.name());
            if (a.isMurderer()) {
                label.append(" (Murderer)");
            } else if (a.isDetective()) {
                label.append(" (Detective)");
            }

            if (!a.isAlive()) {
                label.append(" [DEAD]");
            } else if (!a.hasArrived()) {
                label.append(" [En Route]");
            }

            TreeItem<PersonTreeItemData> leaf = new TreeItem<>(new PersonTreeItemData(label.toString(), a.id(), false));

            if (!a.isAlive()) {
                deceasedBranch.getChildren().add(leaf);
            } else if (a.isDetective()) {
                detectiveBranch.getChildren().add(leaf);
            } else {
                suspectsBranch.getChildren().add(leaf);
            }

            if (a.id().equals(newSelectedId)) {
                itemToSelect = leaf;
            }
        }

        if (!detectiveBranch.getChildren().isEmpty()) {
            root.getChildren().add(detectiveBranch);
        }
        if (!suspectsBranch.getChildren().isEmpty()) {
            root.getChildren().add(suspectsBranch);
        }
        if (!deceasedBranch.getChildren().isEmpty()) {
            root.getChildren().add(deceasedBranch);
        }

        personTreeView.setRoot(root);

        if (itemToSelect != null) {
            personTreeView.getSelectionModel().select(itemToSelect);
        } else {
            if (!detectiveBranch.getChildren().isEmpty()) {
                TreeItem<PersonTreeItemData> first = detectiveBranch.getChildren().get(0);
                personTreeView.getSelectionModel().select(first);
                newSelectedId = first.getValue().agentId();
            } else if (!suspectsBranch.getChildren().isEmpty()) {
                TreeItem<PersonTreeItemData> first = suspectsBranch.getChildren().get(0);
                personTreeView.getSelectionModel().select(first);
                newSelectedId = first.getValue().agentId();
            }
        }

        return newSelectedId;
    }

    public static void renderPersonDetail(VBox contentBox,
                                          SimulationRepository state,
                                          int currentStep,
                                          String selectedPersonId) {
        if (contentBox == null) {
            return;
        }
        contentBox.getChildren().clear();

        SimulationStateSnapshot snap = state.getSnapshotAt(currentStep);
        AgentSnapshot agentSnap = state.getAgentSnapshot(selectedPersonId, currentStep);

        if (agentSnap == null) {
            Label placeholder = new Label("Select a character from the directory on the left to view their detailed profile, secrets, and clues.");
            placeholder.setStyle("-fx-text-fill: #94a3b8; -fx-font-style: italic; -fx-font-size: 14px; -fx-padding: 30;");
            contentBox.getChildren().add(placeholder);
            return;
        }

        VBox headerCard = buildHeaderCard(agentSnap, snap, state);
        VBox profileCard = buildProfileCard(agentSnap, snap, state);
        VBox activityCard = buildActivityLogCard(agentSnap, state, currentStep);
        VBox memCard = buildMemoriesCard(agentSnap, state);
        VBox kbCard = buildKbCard(agentSnap, state);

        contentBox.getChildren().addAll(headerCard, profileCard, activityCard, memCard, kbCard);
    }

    private static VBox buildHeaderCard(AgentSnapshot agentSnap, SimulationStateSnapshot snap, SimulationRepository state) {
        VBox headerCard = new VBox(10);
        headerCard.setPadding(new Insets(16));
        headerCard.setStyle("-fx-background-color: #ffffff; -fx-border-color: #cbd5e1; -fx-border-width: 1px; -fx-background-radius: 8px; -fx-border-radius: 8px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.04), 4, 0, 0, 1);");

        HBox titleRow = new HBox(12);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(agentSnap.name());
        nameLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");
        titleRow.getChildren().add(nameLabel);

        HBox badgesRow = new HBox(8);
        badgesRow.setAlignment(Pos.CENTER_LEFT);

        if (agentSnap.isDetective()) {
            Label detBadge = new Label("🕵️ DETECTIVE");
            detBadge.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 3 10; -fx-background-radius: 12px;");
            badgesRow.getChildren().add(detBadge);
        } else if (agentSnap.isMurderer()) {
            Label murdBadge = new Label("🔪 MURDERER");
            murdBadge.setStyle("-fx-background-color: #dc2626; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 3 10; -fx-background-radius: 12px;");
            badgesRow.getChildren().add(murdBadge);
        } else {
            String roleText = agentSnap.role() != null ? agentSnap.role().toUpperCase() : "GUEST";
            Label guestBadge = new Label("👤 " + roleText);
            guestBadge.setStyle("-fx-background-color: #475569; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 3 10; -fx-background-radius: 12px;");
            badgesRow.getChildren().add(guestBadge);
        }

        if (!agentSnap.isAlive()) {
            Label deadBadge = new Label("💀 DECEASED");
            deadBadge.setStyle("-fx-background-color: #475569; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 3 10; -fx-background-radius: 12px;");
            badgesRow.getChildren().add(deadBadge);
        } else if (!agentSnap.hasArrived()) {
            Label routeBadge = new Label("⏳ EN ROUTE");
            routeBadge.setStyle("-fx-background-color: #d97706; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 3 10; -fx-background-radius: 12px;");
            badgesRow.getChildren().add(routeBadge);
        } else {
            Label aliveBadge = new Label("💚 ALIVE");
            aliveBadge.setStyle("-fx-background-color: #16a34a; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 3 10; -fx-background-radius: 12px;");
            badgesRow.getChildren().add(aliveBadge);
        }

        final String curAgentId = agentSnap.id();
        boolean isVictim = snap != null
                ? snap.agents().values().stream().anyMatch(other -> other.isMurderer() && curAgentId.equals(other.targetVictimId()))
                : state.getAgents().values().stream().anyMatch(other -> other.isMurderer() && curAgentId.equals(other.getTargetVictimId()));
        if (isVictim) {
            Label vicBadge = new Label("🎯 TARGET VICTIM");
            vicBadge.setStyle("-fx-background-color: #9333ea; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 3 10; -fx-background-radius: 12px;");
            badgesRow.getChildren().add(vicBadge);
        }

        headerCard.getChildren().addAll(titleRow, badgesRow);
        return headerCard;
    }

    private static VBox buildProfileCard(AgentSnapshot agentSnap, SimulationStateSnapshot snap, SimulationRepository state) {
        VBox profileCard = new VBox(12);
        profileCard.setPadding(new Insets(16));
        profileCard.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e2e8f0; -fx-border-width: 1px; -fx-background-radius: 8px; -fx-border-radius: 8px;");

        Label profileSectionTitle = new Label("Profile & Background");
        profileSectionTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #1e293b;");

        String locName;
        if (!agentSnap.hasArrived()) {
            locName = "En Route (Has not arrived yet)";
        } else if (agentSnap.currentLocationId() != null) {
            locName = state.resolveLocationName(agentSnap.currentLocationId(), snap.timeStep());
        } else {
            locName = "N/A";
        }

        List<ItemSnapshot> carried = state.getCarriedItems(agentSnap.id(), snap.timeStep());
        String carriedStr = carried.isEmpty() ? "None" : carried.stream().map(ItemSnapshot::name).collect(java.util.stream.Collectors.joining(", "));

        profileCard.getChildren().addAll(
            profileSectionTitle,
            createProfileRow("Current Location:", "📍 " + locName, true),
            createProfileRow("Carried Items:", "🎒 " + carriedStr, false),
            createProfileRow("Role / Occupation:", agentSnap.role() != null ? agentSnap.role() : "Guest", false),
            createProfileRow("Appearance:", agentSnap.appearance() != null ? agentSnap.appearance() : "N/A", false),
            createProfileRow("Personality:", agentSnap.personality() != null ? agentSnap.personality() : "N/A", false),
            createProfileRow("Relationships:", agentSnap.relationship() != null ? agentSnap.relationship() : "N/A", false)
        );

        VBox secretGoalBox = new VBox(6);
        secretGoalBox.setPadding(new Insets(10, 14, 10, 14));
        secretGoalBox.setStyle("-fx-background-color: #fefce8; -fx-border-color: #f59e0b; -fx-border-width: 1px; -fx-background-radius: 6px; -fx-border-radius: 6px;");
        Label sgTitle = new Label("🔒 Secret Goal & Agenda:");
        sgTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #92400e;");
        Label sgText = new Label(agentSnap.secretGoal() != null ? agentSnap.secretGoal() : "None");
        sgText.setWrapText(true);
        sgText.setStyle("-fx-font-style: italic; -fx-font-size: 13px; -fx-text-fill: #78350f;");
        secretGoalBox.getChildren().addAll(sgTitle, sgText);

        profileCard.getChildren().add(secretGoalBox);
        return profileCard;
    }

    private static VBox buildKbCard(AgentSnapshot agentSnap, SimulationRepository state) {
        VBox kbCard = new VBox(10);
        kbCard.setPadding(new Insets(16));
        kbCard.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e2e8f0; -fx-border-width: 1px; -fx-background-radius: 8px; -fx-border-radius: 8px;");

        List<KnowledgeSnapshot> kbList = agentSnap.knowledgeBase();
        int factsCount = kbList != null ? kbList.size() : 0;

        Label kbSectionTitle = new Label("Knowledge Base & Observations (" + factsCount + " clues)");
        kbSectionTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #1e293b;");
        kbCard.getChildren().add(kbSectionTitle);

        if (kbList == null || kbList.isEmpty()) {
            Label emptyKb = new Label("No knowledge or clues recorded yet.");
            emptyKb.setStyle("-fx-text-fill: #94a3b8; -fx-font-style: italic; -fx-font-size: 13px;");
            kbCard.getChildren().add(emptyKb);
        } else {
            VBox kbItemsBox = new VBox(6);
            for (KnowledgeSnapshot k : kbList) {
                HBox kbRow = new HBox(8);
                kbRow.setAlignment(Pos.CENTER_LEFT);
                kbRow.setPadding(new Insets(6, 10, 6, 10));

                String timeStr = state.getClockTime(k.timeStep());
                Label stepBadge = new Label("Step " + k.timeStep() + " (" + timeStr + ")");
                stepBadge.setStyle("-fx-background-color: #e2e8f0; -fx-text-fill: #334155; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 2 6; -fx-background-radius: 4px;");

                Label infoLabel = new Label(k.information());
                infoLabel.setWrapText(true);
                infoLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #1e293b;");
                HBox.setHgrow(infoLabel, Priority.ALWAYS);

                String rowBg = "#f8fafc";
                String rowBorder = "#e2e8f0";
                if (k.information().toLowerCase().contains("dead body") || k.information().toLowerCase().contains("död")) {
                    rowBg = "#fff5f5";
                    rowBorder = "#fca5a5";
                } else if (k.information().toLowerCase().contains("murder") || k.information().toLowerCase().contains("weapon")) {
                    rowBg = "#fffbeb";
                    rowBorder = "#fde68a";
                }

                kbRow.setStyle("-fx-background-color: " + rowBg + "; -fx-border-color: " + rowBorder + "; -fx-border-width: 1px; -fx-background-radius: 4px; -fx-border-radius: 4px;");
                kbRow.getChildren().addAll(stepBadge, infoLabel);
                kbItemsBox.getChildren().add(kbRow);
            }
            kbCard.getChildren().add(kbItemsBox);
        }
        return kbCard;
    }

    private static VBox buildMemoriesCard(AgentSnapshot agentSnap, SimulationRepository state) {
        VBox memCard = new VBox(10);
        memCard.setPadding(new Insets(16));
        memCard.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e2e8f0; -fx-border-width: 1px; -fx-background-radius: 8px; -fx-border-radius: 8px;");

        List<MemorySnapshot> memList = agentSnap.episodicMemories();
        int memCount = memList != null ? memList.size() : 0;

        Label memSectionTitle = new Label("🧠 Episodic Memory & Experiences (" + memCount + " events)");
        memSectionTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #1e293b;");
        memCard.getChildren().add(memSectionTitle);

        if (memList == null || memList.isEmpty()) {
            Label emptyMem = new Label("No episodic memories formed yet. Encounters, persons, and items will be remembered as scenes unfold.");
            emptyMem.setStyle("-fx-text-fill: #94a3b8; -fx-font-style: italic; -fx-font-size: 13px;");
            memCard.getChildren().add(emptyMem);
        } else {
            VBox memItemsBox = new VBox(10);
            for (MemorySnapshot m : memList) {
                VBox mBox = new VBox(6);
                mBox.setPadding(new Insets(8, 12, 8, 12));
                mBox.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #cbd5e1; -fx-border-width: 1px; -fx-background-radius: 6px; -fx-border-radius: 6px;");

                HBox topRow = new HBox(8);
                topRow.setAlignment(Pos.CENTER_LEFT);
                String timeStr = state.getClockTime(m.timeStep());
                Label stepBadge = new Label("Step " + m.timeStep() + " (" + timeStr + ")");
                stepBadge.setStyle("-fx-background-color: #e2e8f0; -fx-text-fill: #334155; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 2 6; -fx-background-radius: 4px;");

                Label locLabel = new Label("📍 " + m.locationName());
                locLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #1e293b;");
                topRow.getChildren().addAll(stepBadge, locLabel);

                String peopleStr = m.presentAgentNames().isEmpty() ? "Nobody" : String.join(", ", m.presentAgentNames());
                Label peopleLabel = new Label("👥 People present: " + peopleStr);
                peopleLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569;");
                peopleLabel.setWrapText(true);

                String itemsStr = m.presentItemNames().isEmpty() ? "None" : String.join(", ", m.presentItemNames());
                Label itemsLabel = new Label("📦 Items in room: " + itemsStr);
                itemsLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569;");
                itemsLabel.setWrapText(true);

                mBox.getChildren().addAll(topRow, peopleLabel, itemsLabel);

                if (!m.observedEvents().isEmpty()) {
                    VBox eventsBox = new VBox(3);
                    eventsBox.setPadding(new Insets(4, 0, 2, 8));
                    Label evTitle = new Label("Witnessed Events:");
                    evTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: #334155;");
                    eventsBox.getChildren().add(evTitle);

                    for (String ev : m.observedEvents()) {
                        Label evItem = new Label("• " + ev);
                        evItem.setWrapText(true);
                        evItem.setStyle("-fx-font-size: 12px; -fx-text-fill: #0f172a;");
                        eventsBox.getChildren().add(evItem);
                    }
                    mBox.getChildren().add(eventsBox);
                }

                memItemsBox.getChildren().add(mBox);
            }
            memCard.getChildren().add(memItemsBox);
        }
        return memCard;
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

    private static VBox buildActivityLogCard(AgentSnapshot agentSnap, SimulationRepository state, int currentStep) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(16));
        card.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e2e8f0; -fx-border-width: 1px; -fx-background-radius: 8px; -fx-border-radius: 8px;");

        List<com.murderhecoded.domain.TimeLocationEvent> events = state.getEventsForAgent(agentSnap.id(), currentStep);
        int evCount = events != null ? events.size() : 0;

        Label title = new Label("📋 Activity Log & Movements (" + evCount + " events)");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #1e293b;");
        card.getChildren().add(title);

        if (events == null || events.isEmpty()) {
            Label empty = new Label("No actions or movements recorded for this character up to Step " + currentStep + ".");
            empty.setStyle("-fx-text-fill: #94a3b8; -fx-font-style: italic; -fx-font-size: 13px;");
            card.getChildren().add(empty);
            return card;
        }

        VBox list = new VBox(8);
        for (com.murderhecoded.domain.TimeLocationEvent ev : events) {
            VBox evBox = new VBox(4);
            evBox.setPadding(new Insets(8, 10, 8, 10));
            evBox.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #cbd5e1; -fx-border-width: 1px; -fx-background-radius: 6px; -fx-border-radius: 6px;");

            HBox topRow = new HBox(8);
            topRow.setAlignment(Pos.CENTER_LEFT);

            String timeStr = state.getClockTime(ev.getTimeStep());
            Label stepBadge = new Label("Step " + ev.getTimeStep() + " (" + timeStr + ")");
            stepBadge.setStyle("-fx-background-color: #e2e8f0; -fx-text-fill: #334155; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 2 6; -fx-background-radius: 4px;");

            String locName = ev.getLocationName() != null ? ev.getLocationName() : state.resolveLocationName(ev.getLocationId(), ev.getTimeStep());
            Label locBadge = new Label("📍 " + locName);
            locBadge.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #1e293b;");

            topRow.getChildren().addAll(stepBadge, locBadge);
            evBox.getChildren().add(topRow);

            if (ev.getActions() != null && !ev.getActions().isEmpty()) {
                for (com.murderhecoded.domain.Action a : ev.getActions()) {
                    boolean isActor = agentSnap.id().equals(a.getActorId());
                    boolean isTarget = agentSnap.id().equals(a.getTargetAgentId());

                    HBox actionRow = new HBox(6);
                    actionRow.setAlignment(Pos.CENTER_LEFT);

                    Label typeBadge = createActionBadge(a.getActionType());
                    actionRow.getChildren().add(typeBadge);

                    StringBuilder sb = new StringBuilder();
                    if (isActor) {
                        sb.append("Performed ").append(a.getActionType().name());
                        if (a.getTargetLocationId() != null) {
                            sb.append(" -> Moved to ").append(state.resolveLocationName(a.getTargetLocationId(), ev.getTimeStep()));
                        }
                        if (a.getTargetAgentId() != null) {
                            sb.append(" with ").append(state.resolveAgentName(a.getTargetAgentId(), ev.getTimeStep()));
                        }
                        if (a.getTargetItemId() != null) {
                            com.murderhecoded.domain.ItemSnapshot it = state.getItemSnapshot(a.getTargetItemId(), ev.getTimeStep());
                            sb.append(" (Item: ").append(it != null ? it.name() : a.getTargetItemId()).append(")");
                        }
                    } else if (isTarget) {
                        sb.append("Targeted by ").append(state.resolveAgentName(a.getActorId(), ev.getTimeStep()))
                          .append(" with ").append(a.getActionType().name());
                    } else {
                        sb.append("Present during ").append(state.resolveAgentName(a.getActorId(), ev.getTimeStep()))
                          .append("'s ").append(a.getActionType().name());
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
            } else {
                Label noActions = new Label("• Present in room during interval.");
                noActions.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b; -fx-font-style: italic;");
                evBox.getChildren().add(noActions);
            }

            list.getChildren().add(evBox);
        }

        card.getChildren().add(list);
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
}
