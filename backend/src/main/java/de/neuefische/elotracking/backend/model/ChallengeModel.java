package de.neuefische.elotracking.backend.model;

import de.neuefische.elotracking.backend.logging.UseToStringForLogging;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
@UseToStringForLogging
@Document(collection = "challenge")
public class ChallengeModel {

    public enum ReportStatus {
        NOT_YET_REPORTED,
        WIN,
        LOSS
    }

    @Id
    private String id;
    private String guildId;
    private String challengerId;
    private String acceptorId;
    private boolean isAccepted;
    private ReportStatus challengerReported;
    private ReportStatus acceptorReported;
    private boolean challengerCalledForCancel = false;
    private boolean acceptorCalledForCancel = false;

    public ChallengeModel(String guildId, String challengerId, String acceptorId) {
        this.id = generateId(guildId, challengerId, acceptorId);
        this.guildId = guildId;
        this.challengerId = challengerId;
        this.acceptorId = acceptorId;
        this.isAccepted = false;
        this.challengerReported = ReportStatus.NOT_YET_REPORTED;
        this.acceptorReported = ReportStatus.NOT_YET_REPORTED;
    }

    public void callForCancel(String playerId) {
        if (playerId.equals(challengerId)) {
            challengerCalledForCancel = true;
        } else {
            acceptorCalledForCancel = true;
        }
    }

    public boolean shouldBeDeleted() {
        if (!isAccepted()) {
            return challengerCalledForCancel || acceptorCalledForCancel;
        } else {
            return challengerCalledForCancel && acceptorCalledForCancel;
        }
    }

    public static String generateId(String channelId, String challengerId, String acceptorId) {
        return challengerId.compareTo(acceptorId) < 0 ?
                String.format("%s-%s-%s", channelId, challengerId, acceptorId) :
                String.format("%s-%s-%s", channelId, acceptorId, challengerId);
    }
    
    public void setGuildId(String guildId) {// TODO kann weg?
        this.guildId = guildId;
        this.id = generateId(guildId, this.challengerId, this.acceptorId);
    }
    
    public void setChallengerId(String challengerId) {
        this.challengerId = challengerId;
        this.id = generateId(guildId, this.challengerId, this.acceptorId);
    }

    public void setAcceptorId(String acceptorId) {
        this.acceptorId = acceptorId;
        this.id = generateId(guildId, this.challengerId, this.acceptorId);
    }
}
