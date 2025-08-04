package com.sopuro.appeal_system.exceptions.appeal;

import com.sopuro.appeal_system.exceptions.AppealException;

public class InvalidAppealDataException extends AppealException {

  public InvalidAppealDataException(String message) {
    super(message);
  }

  public InvalidAppealDataException(String message, Throwable cause) {
    super(message);
  }
}
