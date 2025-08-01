package com.sopuro.appeal_system.clients.opencloud.dtos;

public record RobloxAvatarDto(
    String path,
    boolean done,
    Response response
) {
    public record Response(
        String type,
        String imageUri
    ) {
        // Constructor with @type parameter mapping
        public Response(String type, String imageUri) {
            this.type = type;
            this.imageUri = imageUri;
        }
    }
}