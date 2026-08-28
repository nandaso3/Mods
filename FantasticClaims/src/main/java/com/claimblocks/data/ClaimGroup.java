package com.claimblocks.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ClaimGroup {
    private final UUID groupId;
    private String name;
    private UUID motherClaimId;
    private UUID motherOwnerId;
    private final Set<UUID> registeredPlayers = new HashSet<>();

    public ClaimGroup(UUID uuid, String s, UUID uuid1, UUID uuid2) {
        this.groupId = uuid;
        this.name = s;
        this.motherClaimId = uuid1;
        this.motherOwnerId = uuid2;
        if (uuid2 != null) {
            this.registeredPlayers.add(uuid2);
        }
    }

    public UUID getGroupId() {
        return this.groupId;
    }

    public String getName() {
        return this.name == null ? "Grupo" : this.name;
    }

    public void setName(String s) {
        this.name = s;
    }

    public UUID getMotherClaimId() {
        return this.motherClaimId;
    }

    public void setMotherClaimId(UUID uuid) {
        this.motherClaimId = uuid;
    }

    public UUID getMotherOwnerId() {
        return this.motherOwnerId;
    }

    public Set<UUID> getRegisteredPlayers() {
        return this.registeredPlayers;
    }

    public boolean isRegistered(UUID uuid) {
        return uuid != null && this.registeredPlayers.contains(uuid);
    }

    public void register(UUID uuid) {
        if (uuid != null) {
            this.registeredPlayers.add(uuid);
        }
    }

    public void unregister(UUID uuid) {
        this.registeredPlayers.remove(uuid);
    }

    public JsonObject toJson() {
        JsonObject jsonobject = new JsonObject();
        jsonobject.addProperty("groupId", this.groupId.toString());
        jsonobject.addProperty("name", this.name == null ? "" : this.name);
        jsonobject.addProperty("motherClaimId", this.motherClaimId == null ? "" : this.motherClaimId.toString());
        jsonobject.addProperty("motherOwnerId", this.motherOwnerId == null ? "" : this.motherOwnerId.toString());
        JsonArray jsonarray = new JsonArray();

        for (UUID uuid : this.registeredPlayers) {
            jsonarray.add(uuid.toString());
        }

        jsonobject.add("registered", jsonarray);
        return jsonobject;
    }

    public static ClaimGroup fromJson(JsonObject jsonobject) {
        UUID uuid = UUID.fromString(jsonobject.get("groupId").getAsString());
        String s = jsonobject.has("name") ? jsonobject.get("name").getAsString() : "Grupo";
        UUID uuid1 = jsonobject.has("motherClaimId") && !jsonobject.get("motherClaimId").getAsString().isEmpty()
            ? UUID.fromString(jsonobject.get("motherClaimId").getAsString())
            : null;
        UUID uuid2 = jsonobject.has("motherOwnerId") && !jsonobject.get("motherOwnerId").getAsString().isEmpty()
            ? UUID.fromString(jsonobject.get("motherOwnerId").getAsString())
            : null;
        ClaimGroup claimgroup = new ClaimGroup(uuid, s, uuid1, uuid2);
        if (jsonobject.has("registered")) {
            JsonArray jsonarray = jsonobject.getAsJsonArray("registered");

            for (int i = 0; i < jsonarray.size(); i++) {
                claimgroup.registeredPlayers.add(UUID.fromString(jsonarray.get(i).getAsString()));
            }
        }

        return claimgroup;
    }
}
