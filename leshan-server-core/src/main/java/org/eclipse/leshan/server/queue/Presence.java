/*******************************************************************************
 * Copyright (c) 2017 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 *
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 *
 * Contributors:
 *     Bosch Software Innovations GmbH - initial API
 *******************************************************************************/
package org.eclipse.leshan.server.queue;

/**
 * Possible states of a LWM2M client registered with Queue mode binding.
 */
public enum Presence {
    /**
     * LWM2M Client is reachable and messages can be sent
     **/
    AWAKE,
    /**
     * LWM2M Client is not reachable and no messages can be sent
     **/
    SLEEPING
}
