package de.metas.handlingunits.inventory.impl;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;

import org.compiere.model.I_M_Inventory;
import org.compiere.model.X_C_DocType;

import de.metas.handlingunits.inventory.IHUInventoryBL;
import de.metas.handlingunits.model.I_M_HU;

/*
 * #%L
 * de.metas.handlingunits.base
 * %%
 * Copyright (C) 2017 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

public class HUInventoryBL implements IHUInventoryBL
{
	@Override
	public List<I_M_Inventory> moveToGarbage(final Collection<I_M_HU> husToDestroy, final Timestamp movementDate)
	{
		return HUInternalUseInventoryProducer.newInstance()
				.setMovementDate(movementDate)
				.setDocSubType(X_C_DocType.DOCSUBTYPE_MaterialDisposal)
				.addHUs(husToDestroy)
				.create();
	}

}
