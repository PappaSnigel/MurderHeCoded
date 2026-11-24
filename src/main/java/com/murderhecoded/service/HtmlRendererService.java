package com.murderhecoded.service;

import com.murderhecoded.domain.Agent;
import com.murderhecoded.repository.SimulationRepository;
import java.util.ResourceBundle;

@SuppressWarnings("PMD")
public class HtmlRendererService {

    public String renderPersonDetail(Agent a, SimulationRepository state, ResourceBundle bundle) {
        if (a == null) {
            return "";
        }
        
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><style>")
            .append("body { font-family: 'Segoe UI', system-ui, -apple-system, sans-serif; font-size: 14px; line-height: 1.6; padding: 20px; color: #1e293b; background-color: #f8fafc; -webkit-font-smoothing: antialiased; } ")
            .append(".profile-header { background: #fff; padding: 18px; border-radius: 8px; border: 1px solid #cbd5e1; margin-bottom: 16px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); } ")
            .append(".badge { display: inline-block; padding: 3px 10px; border-radius: 12px; font-size: 11px; font-weight: bold; color: white; margin-right: 6px; } ")
            .append(".card { background: #fff; padding: 16px; border-radius: 8px; border: 1px solid #e2e8f0; margin-bottom: 16px; } ")
            .append(".goal-box { background: #fefce8; border: 1px solid #f59e0b; padding: 10px 14px; border-radius: 6px; margin: 12px 0; font-style: italic; color: #78350f; } ")
            .append("h2 { margin: 0 0 10px 0; font-size: 22px; color: #0f172a; } ")
            .append("h3 { margin: 0 0 10px 0; font-size: 15px; color: #334155; border-bottom: 1px solid #e2e8f0; padding-bottom: 6px; } ")
            .append("p { margin: 6px 0; } ")
            .append("ul { padding-left: 20px; margin: 8px 0; } ")
            .append("li { margin-bottom: 6px; } ")
            .append("</style></head><body>");

        html.append("<div class='profile-header'>");
        html.append("<h2>").append(a.getName()).append("</h2>");
        
        if (a.isMurderer()) {
            html.append("<span class='badge' style='background:#dc2626;'>🔪 MURDERER</span>");
        } else if (a.isDetective()) {
            html.append("<span class='badge' style='background:#2563eb;'>🕵️ DETECTIVE</span>");
        } else {
            String roleText = a.getRole() != null ? a.getRole().toUpperCase() : "GUEST";
            html.append("<span class='badge' style='background:#475569;'>👤 ").append(roleText).append("</span>");
        }
        
        boolean isVictim = state.getAgents().values().stream().anyMatch(other -> other.isMurderer() && a.getId().equals(other.getTargetVictimId()));
        if (isVictim) {
            html.append("<span class='badge' style='background:#9333ea;'>🎯 TARGET VICTIM</span>");
        }
        html.append("</div>");
        
        String lblRole = bundle.containsKey("lbl.role") ? bundle.getString("lbl.role") : "Role:";
        String lblAppearance = bundle.containsKey("lbl.appearance") ? bundle.getString("lbl.appearance") : "Appearance:";
        String lblPersonality = bundle.containsKey("lbl.personality") ? bundle.getString("lbl.personality") : "Personality:";
        String lblSecretGoal = bundle.containsKey("lbl.secretGoal") ? bundle.getString("lbl.secretGoal") : "Secret Goal:";
        String lblStatus = bundle.containsKey("lbl.status") ? bundle.getString("lbl.status") : "Status:";
        String valAlive = bundle.containsKey("lbl.alive") ? bundle.getString("lbl.alive") : "Alive";
        String valDead = bundle.containsKey("lbl.dead") ? bundle.getString("lbl.dead") : "Dead";
        String lblCurrentLoc = bundle.containsKey("lbl.currentLocation") ? bundle.getString("lbl.currentLocation") : "Current Location:";
        String lblKnowledge = bundle.containsKey("lbl.knowledgeBase") ? bundle.getString("lbl.knowledgeBase") : "Knowledge Base";
        String lblNoKnowledge = bundle.containsKey("lbl.noKnowledge") ? bundle.getString("lbl.noKnowledge") : "(No knowledge acquired yet)";
        String lblStep = bundle.containsKey("lbl.step") ? bundle.getString("lbl.step") : "Step";

        html.append("<div class='card'>");
        html.append("<h3>Profile Details</h3>");
        String locName = state.getLocations().containsKey(a.getCurrentLocationId()) ? state.getLocations().get(a.getCurrentLocationId()).getName() : a.getCurrentLocationId();
        html.append("<p><b>").append(lblCurrentLoc).append("</b> 📍 ").append(locName).append("</p>");
        html.append("<p><b>").append(lblStatus).append("</b> ").append(a.isAlive() ? valAlive : valDead).append("</p>");
        html.append("<p><b>").append(lblRole).append("</b> ").append(a.getRole() != null ? a.getRole() : "").append("</p>");
        html.append("<p><b>").append(lblPersonality).append("</b> ").append(a.getPersonality() != null ? a.getPersonality() : "").append("</p>");
        html.append("<p><b>").append(lblAppearance).append("</b> ").append(a.getAppearance() != null ? a.getAppearance() : "").append("</p>");
        
        if (a.getSecretGoal() != null && !a.getSecretGoal().isEmpty()) {
            html.append("<div class='goal-box'><b>🔒 ").append(lblSecretGoal).append("</b> ").append(a.getSecretGoal()).append("</div>");
        }
        html.append("</div>");
        
        html.append("<div class='card'>");
        html.append("<h3>").append(lblKnowledge).append("</h3><ul>");
        if (a.getKnowledgeBase() != null && !a.getKnowledgeBase().isEmpty()) {
            for (com.murderhecoded.domain.Knowledge k : a.getKnowledgeBase()) {
                html.append("<li><span class='badge' style='background:#64748b; font-size:10px;'>").append(lblStep).append(" ").append(k.timeStep()).append("</span> ").append(k.information()).append("</li>");
            }
        } else {
            html.append("<li style='color:#94a3b8; font-style:italic;'>").append(lblNoKnowledge).append("</li>");
        }
        html.append("</ul></div></body></html>");
        return html.toString();
    }
    
    public String renderStory(java.util.List<com.murderhecoded.domain.Chapter> chapters, boolean editedMode, ResourceBundle bundle) {
        return renderStory(chapters, editedMode, bundle, null);
    }

    public String renderStory(java.util.List<com.murderhecoded.domain.Chapter> chapters, boolean editedMode, ResourceBundle bundle, String metadataHtml) {
        StringBuilder html = new StringBuilder();
        html.append("<html><head><meta charset='UTF-8'><style>")
            .append("body { font-family: system-ui, -apple-system, 'Segoe UI', sans-serif; font-size: 16px; line-height: 1.7; padding: 20px; color: #2c3e50; -webkit-font-smoothing: antialiased; text-rendering: optimizeLegibility; } ")
            .append(".chapter-card { margin-bottom: 25px; padding: 16px; border-radius: 6px; box-shadow: 0 1px 3px rgba(0,0,0,0.06); } ")
            .append(".badge { display: inline-block; padding: 3px 10px; border-radius: 12px; font-size: 12px; font-weight: bold; color: white; margin-right: 8px; } ")
            .append(".scores { font-size: 13px; color: #555; } ")
            .append(".transition-bridge { margin: 15px 0; padding: 10px 14px; font-style: italic; color: #7f8c8d; background-color: #f8f9fa; border-left: 3px dashed #b0bec5; border-radius: 4px; font-size: 15px; } ")
            .append("h3 { margin-top: 0; margin-bottom: 8px; color: #1e293b; font-size: 20px; } ")
            .append("p { margin-bottom: 1em; } ")
            .append("</style></head><body>");
        
        for (com.murderhecoded.domain.Chapter c : chapters) {
            boolean isCut = !c.isSelected() || c.category() == com.murderhecoded.domain.SceneCategory.DISPENSABLE || c.isFluff();
            
            // In Edited Mode, compress cut scenes into a narrative bridge
            if (editedMode && isCut) {
                String loc = c.locationName() != null ? c.locationName() : "the estate";
                String timeInfo = (c.timeSpan() != null && !c.timeSpan().isEmpty()) ? c.timeSpan() : ("Step " + c.timeStep());
                html.append("<div class='transition-bridge'>")
                    .append("<i>[Editor's Cut Summary &bull; ").append(timeInfo).append(" &bull; ").append(loc).append("]: ")
                    .append("A period of quiet murmurs and routine movements passed as the characters observed their surroundings.</i>")
                    .append("</div>");
                continue;
            }
            
            String borderColor = "#bdc3c7";
            String bgColor = "#ffffff";
            String badgeText = c.category() != null ? c.category().getDisplayName() : "Scene";
            String badgeBg = "#7f8c8d";
            
            if (c.category() == com.murderhecoded.domain.SceneCategory.ESSENTIAL || c.isPivotal()) {
                borderColor = "#c0392b";
                bgColor = "#fff5f5";
                badgeBg = "#c0392b";
                badgeText = "★ ESSENTIAL CLIMAX";
            } else if (c.category() == com.murderhecoded.domain.SceneCategory.SUPPORTING) {
                borderColor = "#d35400";
                bgColor = "#fffaf0";
                badgeBg = "#d35400";
                badgeText = "SUPPORTING MYSTERY";
            } else if (c.category() == com.murderhecoded.domain.SceneCategory.ATMOSPHERIC) {
                borderColor = "#2980b9";
                bgColor = "#f0f8ff";
                badgeBg = "#2980b9";
                badgeText = "ATMOSPHERIC";
            } else {
                borderColor = "#95a5a6";
                bgColor = "#fafafa";
                badgeBg = "#95a5a6";
                badgeText = "MUNDANE";
            }
            
            html.append("<div class='chapter-card' style='border-left: 6px solid ").append(borderColor)
                .append("; background-color: ").append(bgColor).append(";'>");
            
            String title = (c.dramaticFocus() != null && !c.dramaticFocus().isEmpty()) ? 
                c.dramaticFocus() : ("Scene " + c.sceneNumber() + " - " + c.locationName());
                
            html.append("<h3>").append(title).append("</h3>");
            
            String selectBadge = c.isSelected() ?
                "<span class='badge' style='background-color: #16a34a; font-size: 11px;'>✓ SELECTED FOR FINAL CUT</span>" :
                "<span class='badge' style='background-color: #94a3b8; font-size: 11px;'>✂ CUT FROM FINAL NOVEL</span>";

            String timeDisplay = (c.timeSpan() != null && !c.timeSpan().isEmpty()) ? c.timeSpan() : ("Step " + c.timeStep());

            html.append("<div style='margin-bottom: 12px;'>")
                .append("<span class='badge' style='background-color: ").append(badgeBg).append(";'>").append(badgeText).append("</span>")
                .append(selectBadge)
                .append("<span class='scores'>")
                .append("Importance: <b>").append(String.format("%.1f", c.importanceScore())).append("/10</b> &bull; ")
                .append("Suspense: <b>").append(String.format("%.1f", c.suspenseScore())).append("/10</b> &bull; ")
                .append(timeDisplay).append(" (").append(c.locationName()).append(")")
                .append("</span></div>");
                
            String safeText = c.text() != null ? c.text().replace("*", "") : "";
            html.append("<p>").append(safeText.replace("\n", "<br/>")).append("</p>");
            html.append("</div>");
        }
        
        if (metadataHtml != null && !metadataHtml.isBlank()) {
            html.append(metadataHtml);
        }

        html.append("</body></html>");
        return html.toString();
    }
}
