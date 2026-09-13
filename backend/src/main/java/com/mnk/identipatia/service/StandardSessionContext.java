package com.mnk.identipatia.service;

import com.mnk.identipatia.model.StandardSession;
import com.mnk.identipatia.model.User;

public record StandardSessionContext(StandardSession session, User user) {
}
