/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2020 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.python.checks.hotspots;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.sonar.check.Rule;

@Rule(key = "S3330")
public class HttpOnlyCookieCheck extends AbstractCookieFlagCheck {

  private static Map<String, Integer> sensitiveArgumentByFQN;
  @Override
  String flagName() {
    return "httponly";
  }

  static {
    sensitiveArgumentByFQN = new HashMap<>();
    sensitiveArgumentByFQN.put("django.http.HttpResponse.set_cookie", 7);
    sensitiveArgumentByFQN.put("django.http.HttpResponse.set_signed_cookie", 8);
    sensitiveArgumentByFQN.put("django.http.HttpResponseRedirect.set_cookie", 7);
    sensitiveArgumentByFQN.put("django.http.HttpResponseRedirect.set_signed_cookie", 8);
    sensitiveArgumentByFQN.put("django.http.HttpResponsePermanentRedirect.set_cookie", 7);
    sensitiveArgumentByFQN.put("django.http.HttpResponsePermanentRedirect.set_signed_cookie", 8);
    sensitiveArgumentByFQN.put("django.http.HttpResponseNotModified.set_cookie", 7);
    sensitiveArgumentByFQN.put("django.http.HttpResponseNotModified.set_signed_cookie", 8);
    sensitiveArgumentByFQN.put("django.http.HttpResponseBadRequest.set_cookie", 7);
    sensitiveArgumentByFQN.put("django.http.HttpResponseBadRequest.set_signed_cookie", 8);
    sensitiveArgumentByFQN.put("django.http.HttpResponseNotFound.set_cookie", 7);
    sensitiveArgumentByFQN.put("django.http.HttpResponseNotFound.set_signed_cookie", 8);
    sensitiveArgumentByFQN.put("django.http.HttpResponseForbidden.set_cookie", 7);
    sensitiveArgumentByFQN.put("django.http.HttpResponseForbidden.set_signed_cookie", 8);
    sensitiveArgumentByFQN.put("django.http.HttpResponseNotAllowed.set_cookie", 7);
    sensitiveArgumentByFQN.put("django.http.HttpResponseNotAllowed.set_signed_cookie", 8);
    sensitiveArgumentByFQN.put("django.http.HttpResponseGone.set_cookie", 7);
    sensitiveArgumentByFQN.put("django.http.HttpResponseGone.set_signed_cookie", 8);
    sensitiveArgumentByFQN.put("django.http.HttpResponseServerError.set_cookie", 7);
    sensitiveArgumentByFQN.put("django.http.HttpResponseServerError.set_signed_cookie", 8);
    sensitiveArgumentByFQN.put("flask.wrappers.Response.set_cookie", 7);
    sensitiveArgumentByFQN.put("werkzeug.wrappers.BaseResponse.set_cookie", 7);
    sensitiveArgumentByFQN = Collections.unmodifiableMap(sensitiveArgumentByFQN);
  }

  @Override
  String message() {
    return "Make sure creating this cookie without the \"HttpOnly\" flag is safe.";
  }

  @Override
  Map<String, Integer> sensitiveArgumentByFQN() {
    return sensitiveArgumentByFQN;
  }
}
