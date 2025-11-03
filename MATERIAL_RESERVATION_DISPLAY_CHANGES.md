# Material Reservation Display - Backend Changes Summary

## Overview
Added support for returning `materialReservation` data in `ProductionQueueItem` API responses. This allows the frontend to display detailed material information (type, dimensions, profile) in program cards.

---

## Files Modified

### 1. `ProductionQueueItem.java`
**Path:** `src/main/java/com/example/infraboxapi/productionQueueItem/ProductionQueueItem.java`

**Changes:**
- Added import for `MaterialReservation`
- Added `@OneToOne` relationship to `MaterialReservation`:
  ```java
  // Material Reservation (one-to-one relationship)
  @OneToOne(mappedBy = "productionQueueItem", fetch = FetchType.EAGER)
  @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "productionQueueItem"})
  private MaterialReservation materialReservation;
  ```

**Impact:**
- `materialReservation` will now be automatically included in all API responses returning `ProductionQueueItem`
- Uses `FetchType.EAGER` to ensure reservation is loaded with the item
- `@JsonIgnoreProperties` prevents circular serialization issues

---

### 2. `MaterialGroup.java`
**Path:** `src/main/java/com/example/infraboxapi/materialGroup/MaterialGroup.java`

**Changes:**
- Added `@JsonIgnore` to `fileImage` field to avoid unnecessary data in nested responses
- Changed `materialType` fetch type to `EAGER`:
  ```java
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "material_type_id")
  private MaterialType materialType;
  ```
- Removed `@JsonIgnore` from `materials` list (kept it serialized for direct `MaterialGroup` API calls)

**Impact:**
- `materialType` is now eagerly loaded and included in JSON responses
- `materials` list is still serialized when fetching `MaterialGroup` directly
- Circular reference is prevented by `Material.materialGroup` having `@JsonIgnoreProperties({"materials", ...})`
- Reduces payload size by excluding fileImage in nested contexts

---

## API Response Structure

### Before Changes
```json
{
  "id": 123,
  "partName": "Part A",
  "orderName": "Order 001",
  "materialType": null,
  "quantity": 5
  // ... other fields
}
```

### After Changes

**Example 1: Database Material**
```json
{
  "id": 123,
  "partName": "Part A",
  "orderName": "Order 001",
  "quantity": 5,
  "materialReservation": {
    "id": 456,
    "isCustom": false,
    "quantityOrLength": 5.0,
    "weight": 125.5,
    "cost": 450.00,
    "status": "RESERVED",
    "material": {
      "id": 789,
      "type": "Plate",
      "x": 1000.0,
      "y": 2000.0,
      "z": 10.0,
      "quantity": 15,
      "materialGroup": {
        "id": 10,
        "name": "S235JR Plate Stock",
        "materialType": {
          "id": 1,
          "name": "S235JR",
          "density": 7.85
        }
      }
    }
  }
}
```

**Example 2: Custom Material with Material Type**
```json
{
  "id": 124,
  "partName": "Part B",
  "orderName": "Order 002",
  "quantity": 2,
  "materialReservation": {
    "id": 457,
    "isCustom": true,
    "customName": "Special rod for project XYZ",
    "customType": "ROD",
    "customDiameter": 50.0,
    "customMaterialType": {
      "id": 2,
      "name": "S355J2",
      "density": 7.85
    },
    "quantityOrLength": 1000.0,
    "weight": 15.3,
    "cost": 250.00,
    "status": "RESERVED"
  }
}
```

**Example 3: Custom Material without Material Type**
```json
{
  "id": 125,
  "partName": "Part C",
  "orderName": "Order 003",
  "quantity": 3,
  "materialReservation": {
    "id": 458,
    "isCustom": true,
    "customType": "PLATE",
    "customX": 500.0,
    "customY": 1000.0,
    "customZ": 15.0,
    "customMaterialType": null,
    "quantityOrLength": 2.0,
    "weight": null,
    "cost": 150.00,
    "status": "RESERVED"
  }
}
```

**Example 4: No Material Reservation**
```json
{
  "id": 126,
  "partName": "Part D",
  "orderName": "Order 004",
  "quantity": 1,
  "materialReservation": null
}
```

---

## Affected API Endpoints

All endpoints returning `ProductionQueueItem` or `Page<ProductionQueueItem>` are affected:

1. `GET /api/production-queue-item/nc-queue`
2. `GET /api/production-queue-item/completed`
3. `GET /api/production-queue-item/machine/{machineId}`
4. `GET /api/production-queue-item/{id}`
5. `GET /api/production-queue-item?queueType={type}`
6. `POST /api/production-queue-item/add`
7. `PUT /api/production-queue-item/{id}`
8. `PATCH /api/production-queue-item/{id}/toggle-complete`

---

## Frontend Display

The frontend automatically formats material information based on the reservation data:

- **Database material**: `S235JR • Płyta 10×1000×2000`
- **Custom material with type**: `S355J2 • Pręt ⌀50`
- **Custom material without type**: `Płyta 15×500×1000`
- **No material**: `Material not selected`

The display logic is handled by `formatMaterialDisplayText()` function in:
`ProdQ-CLIENT/src/components/productionQueue/utils/utils.js`

---

## Performance Considerations

### EAGER Fetch Strategy
- `materialReservation` uses `FetchType.EAGER`
- This triggers one additional query per program to load the reservation
- For large queues (100+ programs), consider optimizing with:
  - `@EntityGraph` for batch loading
  - Custom query with `JOIN FETCH`

### Example Optimization (if needed)
```java
@EntityGraph(attributePaths = {"materialReservation", "materialReservation.material",
                                 "materialReservation.material.materialGroup",
                                 "materialReservation.material.materialGroup.materialType"})
Page<ProductionQueueItem> findByQueueType(String queueType, Pageable pageable);
```

---

## Testing

### Manual Testing Steps

1. **Start the backend**:
   ```bash
   ./mvnw spring-boot:run
   ```

2. **Test endpoint**:
   ```bash
   curl -X GET http://localhost:8080/api/production-queue-item/nc-queue \
     -H "Authorization: Bearer YOUR_TOKEN"
   ```

3. **Verify response** includes `materialReservation` field for programs with reservations

4. **Test different scenarios**:
   - Program with database material reservation
   - Program with custom material reservation (with material type)
   - Program with custom material reservation (without material type)
   - Program without material reservation

### Expected Behavior

- All existing programs without reservations: `materialReservation: null`
- Programs with reservations: Full reservation object with nested material/materialType data
- No N+1 query issues (verify in logs)
- No circular serialization errors

---

## Rollback Plan

If issues arise, revert these changes:

1. Remove `materialReservation` field from `ProductionQueueItem.java`
2. Revert `MaterialGroup.java` changes (remove `@JsonIgnore` annotations, revert fetch type)
3. Rebuild and redeploy

---

## Additional Notes

- `MaterialReservation.productionQueueItem` already has `@JsonIgnore`, preventing circular references
- No database migrations required (relationship already exists in DB)
- No service-layer changes needed (serialization handled by JPA/Jackson)
- Frontend is backward-compatible (handles null `materialReservation`)

---

## Related Files

**Frontend:**
- `ProdQ-CLIENT/src/components/productionQueue/utils/utils.js` - Display formatting
- `ProdQ-CLIENT/src/components/productionQueue/ProgramCardContent.jsx` - Card component
- `ProdQ-CLIENT/BACKEND_MATERIAL_RESERVATION_DISPLAY.md` - Original requirements

**Backend:**
- `ProductionQueueItem.java` - Entity with new relationship
- `MaterialReservation.java` - Related entity (no changes)
- `MaterialGroup.java` - Serialization improvements
- `MaterialType.java` - No changes needed
