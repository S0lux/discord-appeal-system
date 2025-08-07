package com.sopuro.appeal_system.shared.permissions;

import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;

public class RoleOverwrites {
    public static class Overseer {
        public static final PermissionSet OPEN_PERMISSIONS = PermissionSet.all();
        public static final PermissionSet CLOSED_PERMISSIONS = PermissionSet.of(
                Permission.VIEW_CHANNEL,
                Permission.READ_MESSAGE_HISTORY);
    }

    public static class Judge {
        public static final PermissionSet OPEN_PERMISSIONS = PermissionSet.of(
                Permission.VIEW_CHANNEL,
                Permission.SEND_MESSAGES,
                Permission.READ_MESSAGE_HISTORY,
                Permission.USE_APPLICATION_COMMANDS,
                Permission.ADD_REACTIONS,
                Permission.ATTACH_FILES);
        public static final PermissionSet CLOSED_PERMISSIONS = PermissionSet.of(
                Permission.VIEW_CHANNEL,
                Permission.READ_MESSAGE_HISTORY);
    }

    public static class Member {
        public static final PermissionSet OPEN_PERMISSIONS = PermissionSet.of(
                Permission.VIEW_CHANNEL,
                Permission.SEND_MESSAGES,
                Permission.READ_MESSAGE_HISTORY,
                Permission.ADD_REACTIONS,
                Permission.ATTACH_FILES);
        public static final PermissionSet CLOSED_PERMISSIONS = PermissionSet.of(
                Permission.VIEW_CHANNEL,
                Permission.READ_MESSAGE_HISTORY);
    }
}
