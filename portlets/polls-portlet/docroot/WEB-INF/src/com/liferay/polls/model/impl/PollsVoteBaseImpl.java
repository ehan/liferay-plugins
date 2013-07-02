/**
 * Copyright (c) 2000-2013 Liferay, Inc. All rights reserved.
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

package com.liferay.polls.model.impl;

import com.liferay.polls.model.PollsVote;
import com.liferay.polls.service.PollsVoteLocalServiceUtil;

import com.liferay.portal.kernel.exception.SystemException;

/**
 * The extended model base implementation for the PollsVote service. Represents a row in the &quot;Polls_PollsVote&quot; database table, with each column mapped to a property of this class.
 *
 * <p>
 * This class exists only as a container for the default extended model level methods generated by ServiceBuilder. Helper methods and all application logic should be put in {@link PollsVoteImpl}.
 * </p>
 *
 * @author Juan Fern√°ndez
 * @see PollsVoteImpl
 * @see com.liferay.polls.model.PollsVote
 * @generated
 */
public abstract class PollsVoteBaseImpl extends PollsVoteModelImpl
	implements PollsVote {
	/*
	 * NOTE FOR DEVELOPERS:
	 *
	 * Never modify or reference this class directly. All methods that expect a polls vote model instance should use the {@link PollsVote} interface instead.
	 */
	@Override
	public void persist() throws SystemException {
		if (this.isNew()) {
			PollsVoteLocalServiceUtil.addPollsVote(this);
		}
		else {
			PollsVoteLocalServiceUtil.updatePollsVote(this);
		}
	}
}