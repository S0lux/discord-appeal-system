package com.sopuro.appeal_system.exceptions.appeal;

import com.sopuro.appeal_system.exceptions.AppealException;

public class UserIsNotRobloxBannedException extends AppealException {
  public UserIsNotRobloxBannedException(String gameName) {
    super(String.format("You are not banned from **%s** on Roblox.\n", gameName));
  }
}
