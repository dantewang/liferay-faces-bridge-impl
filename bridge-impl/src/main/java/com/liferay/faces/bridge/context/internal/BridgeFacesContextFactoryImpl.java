/**
 * Copyright (c) 2000-2025 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */
package com.liferay.faces.bridge.context.internal;

import com.liferay.faces.bridge.context.BridgeFacesContextFactory;

import com.liferay.faces.bridge.util.internal.TCCLUtil;
import jakarta.faces.FacesException;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.faces.context.FacesContextFactory;
import jakarta.faces.lifecycle.Lifecycle;
import jakarta.portlet.ActionRequest;
import jakarta.portlet.EventRequest;
import jakarta.portlet.HeaderRequest;
import jakarta.portlet.PortletContext;
import jakarta.portlet.PortletRequest;
import jakarta.portlet.PortletResponse;
import jakarta.portlet.RenderRequest;
import jakarta.portlet.ResourceRequest;
import jakarta.servlet.http.HttpServletMapping;
import jakarta.servlet.http.HttpServletRequest;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author  Dante Wang
 */
public class BridgeFacesContextFactoryImpl extends BridgeFacesContextFactory {

	@Override
	public FacesContext getFacesContext(FacesContextFactory facesContextFactory, PortletContext portletContext, PortletRequest portletRequest, PortletResponse portletResponse, Lifecycle lifecycle) throws FacesException {
		FacesContext facesContext = facesContextFactory.getFacesContext(portletContext, portletRequest, portletResponse, lifecycle);

		ExternalContext externalContext = facesContext.getExternalContext();

		externalContext.setRequest(_createProxyRequest(portletRequest));

		return facesContext;
	}

	private Object _createProxyRequest(PortletRequest portletRequest) {
		Class<?> clazz = portletRequest.getClass();

		if (portletRequest instanceof ActionRequest) {
			clazz = ActionRequest.class;
		}
		else if (portletRequest instanceof EventRequest) {
			clazz = EventRequest.class;
		}
		else if (portletRequest instanceof HeaderRequest) {
			clazz = HeaderRequest.class;
		}
		else if (portletRequest instanceof RenderRequest) {
			clazz = RenderRequest.class;
		}
		else if (portletRequest instanceof ResourceRequest) {
			clazz = ResourceRequest.class;
		}

		Map<Method, Object> methodMap = new HashMap<>();

		_cacheMethods(methodMap, HttpServletRequest.class, _placeHolder);
		_cacheMethods(methodMap, clazz, portletRequest);

		HttpServletMappingHolder httpServletMappingHolder = new HttpServletMappingHolder();

		return Proxy.newProxyInstance(
			TCCLUtil.getThreadContextClassLoaderOrDefault(BridgeFacesContextFactoryImpl.class),
			new Class<?>[]{clazz, HttpServletRequest.class, HttpServletMappingAware.class},
			(proxy, method, args) -> {
				if (Objects.equals(method.getName(), "setHttpServletMapping") && (args.length == 1)) {
					httpServletMappingHolder.httpServletMapping = (HttpServletMapping)args[0];

					return null;
				}

				Object target = methodMap.get(method);

				if (target == _placeHolder) {
					if (Objects.equals(method.getName(), "getHttpServletMapping")) {
						return httpServletMappingHolder.httpServletMapping;
					}
					else {
						throw new UnsupportedOperationException(method.getName());
					}
				}

				return method.invoke(Objects.requireNonNullElse(target, portletRequest), args);
			}
		);
	}

	private void _cacheMethods(Map<Method, Object> methodMap, Class<?> interfaceClass, Object object) {
		for (Method method : interfaceClass.getMethods()) {
			methodMap.put(method, object);
		}
	}

	private static class HttpServletMappingHolder {
		private HttpServletMapping httpServletMapping;
	}

	private static final Object _placeHolder = new Object();

}