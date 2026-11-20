package tech.cocok.app.data.local

import tech.cocok.app.domain.model.Catalog
import tech.cocok.app.domain.model.Confirmation
import tech.cocok.app.domain.model.Fitment
import tech.cocok.app.domain.model.FitmentGroup
import tech.cocok.app.domain.model.GroupPart
import tech.cocok.app.domain.model.Part
import tech.cocok.app.domain.model.PartCategory
import tech.cocok.app.domain.model.MatchEvidence
import tech.cocok.app.domain.model.MatchKind
import tech.cocok.app.domain.model.Role
import tech.cocok.app.domain.model.Status
import tech.cocok.app.domain.model.Sumber
import tech.cocok.app.domain.model.Vehicle

internal data class CatalogRows(
    val vehicles: List<VehicleEntity>,
    val categories: List<PartCategoryEntity>,
    val parts: List<PartEntity>,
    val groups: List<FitmentGroupEntity>,
    val groupParts: List<GroupPartEntity>,
    val fitments: List<FitmentEntity>,
    val synonyms: List<SynonymEntity>,
    val confirmations: List<ConfirmationEntity>,
    val matchEvidence: List<MatchEvidenceEntity>,
) {
    fun toCatalog(): Catalog = Catalog(
        vehicles = vehicles.map { row ->
            Vehicle(
                id = row.id,
                brand = row.brand,
                model = row.model,
                generation = row.generation,
                yearFrom = row.yearFrom,
                yearTo = row.yearTo,
                attributes = row.attributes,
                verified = row.verified,
            )
        },
        categories = categories.map { row ->
            PartCategory(id = row.id, name = row.name, position = row.position)
        },
        parts = parts.map { row ->
            Part(
                id = row.id,
                categoryId = row.categoryId,
                name = row.name,
                brand = row.brand,
                partNumber = row.partNumber,
                isOriginal = row.isOriginal,
                photoKey = row.photoKey,
                specifications = row.specifications,
                notes = row.notes,
            )
        },
        groups = groups.map { row ->
            FitmentGroup(id = row.id, categoryId = row.categoryId, label = row.label, notes = row.notes)
        },
        groupParts = groupParts.map { row ->
            GroupPart(groupId = row.groupId, partId = row.partId, status = Status.valueOf(row.status))
        },
        fitments = fitments.map { row ->
            Fitment(
                id = row.id,
                groupId = row.groupId,
                vehicleId = row.vehicleId,
                yearFrom = row.yearFrom,
                yearTo = row.yearTo,
                constraints = row.constraints,
                status = Status.valueOf(row.status),
                sumber = Sumber.valueOf(row.sumber),
                confirmedByRole = row.confirmedByRole?.let(Role::valueOf),
                confirmedAt = row.confirmedAt,
            )
        },
        synonyms = synonyms.associateTo(LinkedHashMap(synonyms.size)) { it.term to it.replacement },
        confirmations = confirmations.map { row ->
            Confirmation(
                id = row.id,
                fitmentId = row.fitmentId,
                groupId = row.groupId,
                oleh = row.oleh,
                role = Role.valueOf(row.role),
                hasil = row.hasil,
                catatan = row.catatan,
                waktu = row.waktu,
            )
        },
        matchEvidence = matchEvidence.map { row ->
            MatchEvidence(
                id = row.id,
                firstPartId = row.firstPartId,
                secondPartId = row.secondPartId,
                vehicleId = row.vehicleId,
                year = row.year,
                variant = row.variant,
                kind = MatchKind.valueOf(row.kind),
                note = row.note,
                by = row.oleh,
                role = Role.valueOf(row.role),
                createdAt = row.createdAt,
            )
        },
    )

    companion object {
        suspend fun read(dao: CatalogDao): CatalogRows = CatalogRows(
            vehicles = dao.vehicles(),
            categories = dao.categories(),
            parts = dao.parts(),
            groups = dao.groups(),
            groupParts = dao.groupParts(),
            fitments = dao.fitments(),
            synonyms = dao.synonyms(),
            confirmations = dao.confirmations(),
            matchEvidence = dao.matchEvidence(),
        )

        fun fromCatalog(catalog: Catalog): CatalogRows = CatalogRows(
            vehicles = catalog.vehicles.mapIndexed { index, value ->
                VehicleEntity(
                    id = value.id,
                    brand = value.brand,
                    model = value.model,
                    generation = value.generation,
                    yearFrom = value.yearFrom,
                    yearTo = value.yearTo,
                    attributes = value.attributes,
                    verified = value.verified,
                    sortOrder = index,
                )
            },
            categories = catalog.categories.mapIndexed { index, value ->
                PartCategoryEntity(id = value.id, name = value.name, position = value.position, sortOrder = index)
            },
            parts = catalog.parts.mapIndexed { index, value ->
                PartEntity(
                    id = value.id,
                    categoryId = value.categoryId,
                    name = value.name,
                    brand = value.brand,
                    partNumber = value.partNumber,
                    isOriginal = value.isOriginal,
                    photoKey = value.photoKey,
                    specifications = value.specifications,
                    notes = value.notes,
                    sortOrder = index,
                )
            },
            groups = catalog.groups.mapIndexed { index, value ->
                FitmentGroupEntity(
                    id = value.id,
                    categoryId = value.categoryId,
                    label = value.label,
                    notes = value.notes,
                    sortOrder = index,
                )
            },
            groupParts = catalog.groupParts.mapIndexed { index, value ->
                GroupPartEntity(
                    groupId = value.groupId,
                    partId = value.partId,
                    status = storedStatus(value.status),
                    sortOrder = index,
                )
            },
            fitments = catalog.fitments.mapIndexed { index, value ->
                FitmentEntity(
                    id = value.id,
                    groupId = value.groupId,
                    vehicleId = value.vehicleId,
                    yearFrom = value.yearFrom,
                    yearTo = value.yearTo,
                    constraints = value.constraints,
                    status = storedStatus(value.status),
                    sumber = value.sumber.name,
                    confirmedByRole = value.confirmedByRole?.name,
                    confirmedAt = value.confirmedAt,
                    sortOrder = index,
                )
            },
            synonyms = catalog.synonyms.entries.mapIndexed { index, value ->
                SynonymEntity(term = value.key, replacement = value.value, sortOrder = index)
            },
            confirmations = catalog.confirmations.mapIndexed { index, value ->
                ConfirmationEntity(
                    id = value.id,
                    fitmentId = value.fitmentId,
                    groupId = value.groupId,
                    oleh = value.oleh,
                    role = value.role.name,
                    hasil = value.hasil,
                    catatan = value.catatan,
                    waktu = value.waktu,
                    sortOrder = index,
                )
            },
            matchEvidence = catalog.matchEvidence.mapIndexed { index, value ->
                MatchEvidenceEntity(
                    id = value.id,
                    firstPartId = value.firstPartId,
                    secondPartId = value.secondPartId,
                    vehicleId = value.vehicleId,
                    year = value.year,
                    variant = value.variant,
                    kind = value.kind.name,
                    note = value.note,
                    oleh = value.by,
                    role = value.role.name,
                    createdAt = value.createdAt,
                    sortOrder = index,
                )
            },
        )

        private fun storedStatus(status: Status): String {
            require(status.tersimpan) { "BELUM_ADA_DATA is derived and cannot be stored in the catalog" }
            return status.name
        }
    }
}
