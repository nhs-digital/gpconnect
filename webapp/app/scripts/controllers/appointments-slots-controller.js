'use strict';

angular.module('gpConnect')
        .controller('AppointmentsSlotsCtrl', function ($stateParams, $scope, usSpinnerService, Appointment, appointmentSearchParams, $modalInstance, $modal, modal, PatientService, ProviderRouting) {

            PatientService.getFhirPatient(ProviderRouting.defaultPractice().odsCode, $stateParams.patientId).then(function (patient) {
                $scope.patientDetails = patient;
            });

            $scope.selectedSlots = [];
            var slotEnum = {
                ADJACENT: 0,
                NOTADJACENT: 1,
                SAMESLOT: 2,
                SAMESLOTMIDDLE: 3
            };

            var numberOfSearches = 0;
            usSpinnerService.spin('appointmentSlots-spinner');

            $scope.ganttModel = [];
            $scope.ganttHeaderFormats = {
                day: 'DD MMMM YYYY',
                hour: 'HH:mm'
            };

            var internalGetScheduleModel = {};
            $scope.practicesSearchingAndFails = [];

            var startDate = moment(appointmentSearchParams.startDate).format('YYYY-MM-DDTHH:mm:ssZ');
            var endDate = moment(appointmentSearchParams.endDate).add(23, 'hours').add(59, 'minutes').add(59, 'seconds').format('YYYY-MM-DDTHH:mm:ssZ');

            var poputlateModel = function (practiceOdsCode, practiceName, practiceOrgType, startDate, endDate, primary) {

                var practiceState = {};
                practiceState.practiceName = practiceName;
                practiceState.practiceOdsCode = practiceOdsCode;
                practiceState.practiceOrgType = practiceOrgType;
                practiceState.status = "Searching";
                $scope.practicesSearchingAndFails.push(practiceState);

                numberOfSearches++;
                usSpinnerService.spin('appointmentSlots-spinner');

                var responseSlots = [];             // Slots is an array to cycle through easily
                var responseSchedules = {};         // We will lookup schedule from slots so we can use the schedule reference as a key in the key value pair
                var responsePractitioners = {};     // We will lookup practitioner from the schedule using reference so we can use the reference as the key/name
                var responseLocations = {};         // We will lookup location from the schedule using reference so we can use the reference as the key/name

                Appointment.searchForFreeSlots(practiceOdsCode, practiceOrgType, startDate, endDate).then(function (result) {

                    var getScheduleJson = result.data;

                    if (!getScheduleJson || !getScheduleJson.entry) {
                        getScheduleJson = {entry: []};
                    }

                    var getPractitionerRef = function (value) {
                        for (var i = 0; i < value.resource.actor.length; i++) {
                            if (value.resource.actor[i] && value.resource.actor[i].reference.startsWith("Practitioner")) {
                                return value.resource.actor[i].reference;
                            }
                        }
                        return null;
                    };

                    var getLocationRef = function (value) {
                        for (var i = 0; i < value.resource.actor.length; i++) {
                            if (value.resource.actor[i] && value.resource.actor[i].reference.startsWith("Location")) {
                                return value.resource.actor[i].reference;
                            }
                        }
                        return null;
                    };

                    var getExtensionCoding = function (value, url) {
                        for (var i = 0; i < value.resource.extension.length; i++) {
                            if (value.resource.extension[i] && value.resource.extension[i].url === url) {
                                return value.resource.extension[i].valueCodeableConcept.coding;
                            }
                        }
                        return null;
                    };

                    var getExtensionValueCoding = function (value, url) {
                        for (var i = 0; i < value.resource.extension.length; i++) {
                            if (value.resource.extension[i] && value.resource.extension[i].url === url) {
                                //console.log(value.resource.extension[i]);
                                return value.resource.extension[i].valueCode;
                            }
                        }
                        return null;
                    };

                    var getMultipleExtensionCoding = function (value, url) {
                        var list = [];
                        for (var i = 0; i < value.resource.extension.length; i++) {
                            if (value.resource.extension[i] && value.resource.extension[i].url === url) {
                                list.push(value.resource.extension[i].valueCodeableConcept.coding);
                            }
                        }
                        return list;
                    }

                    // go through response and build arrays of all returned data
                    $.each(getScheduleJson.entry, function (key, value) {
                        if (value.resource.resourceType == "Slot") {
                            var slot = {"scheduleRef": value.resource.schedule.reference,
                                "startDateTime": value.resource.start,
                                "endDateTime": value.resource.end,
                                // #158 slot no longer carries serviceType
                                // "type": value.resource.serviceType[0].coding[0].display, 
                                // "typeCode": value.resource.serviceType[0].coding[0].code,
                                "id": value.resource.id,
                                "deliveryChannelCoding": getExtensionValueCoding(value, "https://fhir.nhs.uk/STU3/StructureDefinition/Extension-GPConnect-DeliveryChannel-2")};
                            responseSlots.push(slot);
                        }

                        if (value.resource.resourceType == "Schedule") {
                            // NB add as a relative reference not absolute
                            responseSchedules["Schedule/"+value.resource.id] = {
                                "locationRef": getLocationRef(value),
                                "practitionerRef": getPractitionerRef(value),
                                "practitionerRoleCoding": getExtensionCoding(value, "https://fhir.nhs.uk/STU3/StructureDefinition/Extension-GPConnect-PractitionerRole-1")
                            };
                        }
                        if (value.resource.resourceType == "Practitioner") {
                            var prefix = "";
                            var given = "";
                            var family = "";
                            if (value.resource.name[0].prefix != undefined) {
                                prefix = value.resource.name[0].prefix[0];
                            }
                            if (value.resource.name[0].given[0] != undefined) {
                                given = value.resource.name[0].given[0];
                            }
                            if (value.resource.name[0].family != undefined) {
                                family = value.resource.name[0].family;
                            }
                            var fullName = prefix + " " + given + " " + family;
                            // NB relative not absolute
                            responsePractitioners["Practitioner/"+value.resource.id] = {
                                "fullName": fullName,
                                "id": value.resource.id
                            };
                        }
                        if (value.resource.resourceType == "Location") {
                            var addressStr = value.resource.name;
                            if (value.resource.address != undefined) {
                                if (value.resource.address.line != undefined) {
                                    addressStr += ", " + value.resource.address.line;
                                }
                                if (value.resource.address.city != undefined) {
                                    addressStr += ", " + value.resource.address.city;
                                }
                                if (value.resource.address.district != undefined) {
                                    addressStr += ", " + value.resource.address.district;
                                }
                                if (value.resource.address.state != undefined) {
                                    addressStr += ", " + value.resource.address.state;
                                }
                                if (value.resource.address.postalCode != undefined) {
                                    addressStr += ", " + value.resource.address.postalCode;
                                }
                                if (value.resource.address.country != undefined) {
                                    addressStr += ", " + value.resource.address.country;
                                }
                            }
                            // NB relative location ref not
                            responseLocations["Location/"+value.resource.id] = {"id": value.resource.id, "address": addressStr};
                        }
                    });

                    if (responseSlots.length > 0) {
                        $.each(responseSlots, function (key, value) {
                            // Build slot object

                            var slot = {"startDateTime": new Date(value.startDateTime), "endDateTime": new Date(value.endDateTime), "type": value.type, "typeCode": value.typeCode, "id": value.id,
                                "deliveryChannelCoding": value.deliveryChannelCoding};
                            // Find the schedule for that slot from array
                            var schedule = responseSchedules[value.scheduleRef];
                            // Find Location and practitioner for that schedule

                            var practitionerName = responsePractitioners[schedule.practitionerRef] ? responsePractitioners[schedule.practitionerRef].fullName : null;
                            var practitionerId = responsePractitioners[schedule.practitionerRef] ? responsePractitioners[schedule.practitionerRef].id : null;
                            var locationId = responseLocations[schedule.locationRef].id;
                            var locationAddress = responseLocations[schedule.locationRef].address;
                            var practitionerRole = schedule.practitionerRoleCoding[0].display;
                            // Look at constructed object to see if the location exists already, is so add to existing or add new
                            if (internalGetScheduleModel.locations == undefined) {
                                var slots = [];
                                slots.push(slot);
                                var practitioners = [];
                                var practitionerModel = {"fullName": practitionerName, "slots": slots, "id": practitionerId, "role": practitionerRole};
                                practitioners.push(practitionerModel);
                                var dayBlockOfSlots = [];
                                var startDateTime = new Date(slot.startDateTime);
                                var dayBlockOfSlotsModel = {"dayOfWeek": getDayFromDate(slot.startDateTime), "date": startDateTime.setHours(0, 0, 0, 0), "freeSlots": 1, "practitioners": practitioners};
                                dayBlockOfSlots.push(dayBlockOfSlotsModel);
                                var locations = [];
                                var locationModel = {"address": locationAddress, "freeSlots": 1, "dayBlockOfSlots": dayBlockOfSlots, "id": locationId, "odsCode": practiceOdsCode, "practiceName": practiceName, "primary": primary};
                                locations.push(locationModel);
                                internalGetScheduleModel.locations = locations;
                            } else {
                                var locationIndex = -1;
                                // search locations for location
                                for (var i = 0; i < internalGetScheduleModel.locations.length; i++) {
                                    if (locationId == internalGetScheduleModel.locations[i].id /*&& practiceOdsCode == internalGetScheduleModel.locations[i].odsCode*/) {
                                        locationIndex = i;
                                        break;
                                    }
                                }
                                ;
                                if (locationIndex == -1) {
                                    // if location is not found we need to build all the structure add it to the model
                                    var slots = [];
                                    slots.push(slot);
                                    var practitioners = [];
                                    var practitionerModel = {"fullName": practitionerName, "slots": slots, "id": practitionerId, "role": practitionerRole};
                                    practitioners.push(practitionerModel);
                                    var dayBlockOfSlots = [];
                                    var startDateTime = new Date(slot.startDateTime);
                                    var dayBlockOfSlotsModel = {"dayOfWeek": getDayFromDate(slot.startDateTime), "date": startDateTime.setHours(0, 0, 0, 0), "freeSlots": 1, "practitioners": practitioners};
                                    var locationModel = {"address": locationAddress, "freeSlots": 1, "dayBlockOfSlots": dayBlockOfSlots, "id": locationId, "odsCode": practiceOdsCode, "practiceName": practiceName, "primary": primary};
                                    internalGetScheduleModel.locations.push(locationModel);
                                } else {
                                    // else get the existing model and add the new slot to it.
                                    internalGetScheduleModel.locations[locationIndex].freeSlots++;  // Increment free slots
                                    //try and find the dayBlockOfSlots for same date
                                    var dayBlockOfSlotsIndex = -1;
                                    for (var i = 0; i < internalGetScheduleModel.locations[locationIndex].dayBlockOfSlots.length; i++) {
                                        var startDateTime = new Date(slot.startDateTime);
                                        if (startDateTime.setHours(0, 0, 0, 0) == internalGetScheduleModel.locations[locationIndex].dayBlockOfSlots[i].date) {
                                            dayBlockOfSlotsIndex = i;
                                            break;
                                        }
                                    }
                                    ;
                                    if (dayBlockOfSlotsIndex == -1) {
                                        // If we did not find a block of slots we need to make one
                                        var slots = [];
                                        slots.push(slot);
                                        var practitioners = [];
                                        var practitionerModel = {"fullName": practitionerName, "slots": slots, "id": practitionerId, "role": practitionerRole};
                                        practitioners.push(practitionerModel);
                                        var dayBlockOfSlots = [];
                                        var startDateTime = new Date(slot.startDateTime);
                                        var dayBlockOfSlotsModel = {"dayOfWeek": getDayFromDate(slot.startDateTime), "date": startDateTime.setHours(0, 0, 0, 0), "freeSlots": 1, "practitioners": practitioners};
                                        internalGetScheduleModel.locations[locationIndex].dayBlockOfSlots.push(dayBlockOfSlotsModel);
                                    } else {
                                        // If we have found a block of slots for the day we can see if the practitioner exists and is so add our new slot to them
                                        internalGetScheduleModel.locations[locationIndex].dayBlockOfSlots[dayBlockOfSlotsIndex].freeSlots++;  // Increment free slots
                                        var practitionerIndex = -1;
                                        for (var i = 0; i < internalGetScheduleModel.locations[locationIndex].dayBlockOfSlots[dayBlockOfSlotsIndex].practitioners.length; i++) {
                                            if (practitionerId == internalGetScheduleModel.locations[locationIndex].dayBlockOfSlots[dayBlockOfSlotsIndex].practitioners[i].id) {
                                                practitionerIndex = i;
                                                break;
                                            }
                                        }
                                        ;
                                        if (practitionerIndex == -1) {
                                            // If we did not find a block of slots we need to make one
                                            var slots = [];
                                            slots.push(slot);
                                            var practitionerModel = {"fullName": practitionerName, "slots": slots, "id": practitionerId, "role": practitionerRole};
                                            internalGetScheduleModel.locations[locationIndex].dayBlockOfSlots[dayBlockOfSlotsIndex].practitioners.push(practitionerModel);
                                        } else {
                                            internalGetScheduleModel.locations[locationIndex].dayBlockOfSlots[dayBlockOfSlotsIndex].practitioners[practitionerIndex].slots.push(slot);
                                        }
                                    }
                                }

                            }

                        });

                        $scope.scheduleModel = internalGetScheduleModel;
                        // select the default location
                        $scope.selectDefaultLocation(internalGetScheduleModel.locations);
                        setTimeout(function () {
                            $('.gantt-scrollable').scrollLeft(100);
                            //gantt-side
                            $(".gantt-side").css("width", "auto");
                        }, 10);
                    }
                    numberOfSearches--;
                    if (numberOfSearches <= 0) {
                        usSpinnerService.stop('appointmentSlots-spinner');
                    }
                    if (responseSlots.length > 0) {
                        var indexOfElement = $scope.practicesSearchingAndFails.indexOf(practiceState);
                        $scope.practicesSearchingAndFails.splice(indexOfElement, 1);
                        practiceState.status = "Success";
                    } else {
                        practiceState.status = "No Slots";
                    }

                    if (result.status !== 200) {
                        practiceState.status = "Failed";
                    }
                }, function (result) {
                    practiceState.status = "Failed";
                    numberOfSearches--;
                    if (numberOfSearches <= 0) {
                        usSpinnerService.stop('appointmentSlots-spinner');
                    }
                });
            };

            if (appointmentSearchParams.primaryPractice.checked == true) {
                poputlateModel(appointmentSearchParams.primaryPractice.odsCode, appointmentSearchParams.primaryPractice.name, appointmentSearchParams.primaryPractice.orgType, startDate, endDate, true);
            }
            $.each(appointmentSearchParams.federatedPractices, function (index, practice) {
                if (practice.checked == true) {
                    poputlateModel(practice.odsCode, practice.name, practice.orgType, startDate, endDate, false);
                }
            });


            $scope.loadDaySlots = function (day) {
                $scope.ganttModel = [];

                var practitioners = day.practitioners;

                for (var i = 0; i < practitioners.length; i++) {
                    var practitionerSchedule = {
                        id: practitioners[i].id,
                        name: practitioners[i].fullName,
                        role: practitioners[i].role,
                        height: '3em',
                        sortable: false,
                        tasks: []
                    };

                    var slots = practitioners[i].slots;
                    for (var j = 0; j < slots.length; j++) {
                        var slot = {
                            name: slots[j].type,
                            typeCode: slots[j].typeCode,
                            color: '#99ccff',
                            from: slots[j].startDateTime,
                            to: slots[j].endDateTime,
                            id: slots[j].id,
                            deliveryChannelCoding: slots[j].deliveryChannelCoding
                        };
                        practitionerSchedule.tasks.push(slot);
                    }

                    $scope.ganttModel.push(practitionerSchedule);
                    $scope.displayFromDate = day.date;
                    $scope.displayToDate = day.date;


                }
            };

            $scope.options = {
                ganttTableColumns: ['model.name', 'model.role'],
                ganttTableColumnHeaders: {'model.name': 'Practitioner', 'model.role': 'Role'}
            };

            $scope.onSelectLocation = function (location) {
                $scope.selectedLocation = location;
                // grab the default day and select it
                $scope.selectDefaultDay(location);
                clearSelectedSlots();
            };

            $scope.isLocationSelected = function (location) {
                return location === $scope.selectedLocation;
            };

            $scope.selectDefaultLocation = function (locations) {
                // grab the first day and select it
                if (locations.length > 0) {
                    var firstLocation = locations[0];
                    for (var peimaryLocationIndex = 0; peimaryLocationIndex < locations.length; peimaryLocationIndex++) {
                        if (locations[peimaryLocationIndex].primary == true) {
                            firstLocation = locations[peimaryLocationIndex];
                            peimaryLocationIndex = locations.length;
                        }
                    }
                    $scope.onSelectLocation(firstLocation);
                }
            };

            $scope.isDaySelected = function (day) {
                return day === $scope.selectedDay;
            };

            $scope.onSelectDay = function (day) {
                $scope.selectedDay = day;
                $scope.loadDaySlots(day);
                clearSelectedSlots();
            };

            $scope.selectDefaultDay = function (location) {
                // grab the first day and select it
                if (location.dayBlockOfSlots.length > 0) {
                    var firstDay = location.dayBlockOfSlots[0];
                    $scope.onSelectDay(firstDay);
                }
            };

            $scope.cancel = function () {
                $modalInstance.dismiss('cancel');
            };

            $scope.getFormattedDate = function (day) {
                var date = new Date(day.date);
                var options = {weekday: 'short', year: '2-digit', month: 'short', day: 'numeric'};
                var formattedDate = date.toLocaleString('en-GB', options);

                return formattedDate;
            };

            $scope.bookAppointment = function () {
                if ($scope.selectedSlots.length <= 0) {
                    alert("You must select slots before booking an appointment");
                }

                usSpinnerService.spin('appointmentSlots-spinner');

                var earliestFromDate = $scope.selectedSlots[0].model.from;
                var lastToDate = $scope.selectedSlots[0].model.to;
                var slotIds = [];
                for (var slotIndex = 0; slotIndex < $scope.selectedSlots.length; slotIndex++) {
                    slotIds.push($scope.selectedSlots[slotIndex].model.id);
                    if ($scope.selectedSlots[slotIndex].model.from.isBefore(earliestFromDate)) {
                        earliestFromDate = $scope.selectedSlots[slotIndex].model.from;
                    }
                    if ($scope.selectedSlots[slotIndex].model.to.isAfter(lastToDate)) {
                        lastToDate = $scope.selectedSlots[slotIndex].model.to;
                    }
                }

                $scope.appointmentBookingParameters = {};
                $scope.appointmentBookingParameters.patient = $scope.patientDetails;
                $scope.appointmentBookingParameters.location = $scope.selectedLocation;
                $scope.appointmentBookingParameters.slotIds = slotIds;
                $scope.appointmentBookingParameters.startTime = earliestFromDate;
                $scope.appointmentBookingParameters.endTime = lastToDate;
                $scope.appointmentBookingParameters.practitionerId = $scope.selectedSlots[0].row.model.id;
                $scope.appointmentBookingParameters.practitionerFullName = $scope.selectedSlots[0].row.model.name;
                $scope.appointmentBookingParameters.practitionerRole = $scope.selectedSlots[0].row.model.role;
                var tasks = $scope.selectedSlots[0].row.model.tasks;
                for (var taskIndex = 0; taskIndex < tasks.length; taskIndex++) {
                    if (slotIds[0] === tasks[taskIndex].id) {
                        var startTask = tasks[taskIndex];
                        $scope.appointmentBookingParameters.deliveryChannel = startTask.deliveryChannelCoding;
                        break;
                    }
                }
                $scope.appointmentBookingParameters.locationId = $scope.selectedLocation.id;
                // removed #158 slots don't carry type code
//              $scope.appointmentBookingParameters.typeCode = $scope.selectedSlots[0].model.typeCode;
                $scope.appointmentBookingParameters.type = $scope.selectedSlots[0].model.name;

                // Check the patient is on the remote system
                PatientService.getPatientFhirId($stateParams.patientId, "B82617").then(function (patientFhirIDResult) {

                    if (patientFhirIDResult == undefined) {
                        // The patient does not exist on the remote system so needs to be created
                        usSpinnerService.stop('appointmentSlots-spinner');
                        $modalInstance.close();
                        $modal.open({
                            templateUrl: 'views/appointments/appointments-patient-create-modal.html',
                            size: 'md',
                            controller: 'AppointmentsPatientCreateModalCtrl',
                            resolve: {
                                patient: function () {
                                    return $scope.patientDetails;
                                },
                                appointmentBookingParameters: function () {
                                    return $scope.appointmentBookingParameters;
                                }
                            }
                        });
                    } else {
                        // Patient already exists so just create the appointment
                        usSpinnerService.stop('appointmentSlots-spinner');
                        $modalInstance.close();
                        $modal.open({
                            templateUrl: 'views/appointments/appointments-create-modal.html',
                            size: 'md',
                            controller: 'AppointmentsCreateModalCtrl',
                            resolve: {
                                modal: function () {
                                    return {
                                        title: 'Book Appointment'
                                    };
                                },
                                appointmentBookingParams: function () {
                                    $scope.appointmentBookingParameters.patientFhirId = patientFhirIDResult;
                                    return $scope.appointmentBookingParameters;
                                }
                            }
                        });
                    }
                }, function (result) {
                    // Error calling the remote server
                    alert($scope.appointmentBookingParameters.location.practiceName + " cannot be connected to at this current time");
                    $modalInstance.dismiss('cancel');
                });
            };

            var slotAdjacent = function (task) {
                if ($scope.selectedSlots.length > 0) {
                    // check the practitioner is the same practitioner
                    if ($scope.selectedSlots[0].row.model.id != task.row.model.id) {
                        // Slot is for a different practitioner
                        return slotEnum.NOTADJACENT;
                    }

                    var sameSlot = false;
                    var adjacentSlotToLeft = false;
                    var adjacentSlotToRight = false;

                    // If the slot start date or end date match one in the list of currently selected slots then adjacent (Ignoring seconds accuracy as not indicated on front end)
                    for (var slotIndex = 0; slotIndex < $scope.selectedSlots.length; slotIndex++) {
                        if (task.model.from.isSame($scope.selectedSlots[slotIndex].model.from) && task.model.to.isSame($scope.selectedSlots[slotIndex].model.to)) {
                            sameSlot = true;
                        }
                        if (task.model.from.seconds(0).milliseconds(0).isSame($scope.selectedSlots[slotIndex].model.to.seconds(0).milliseconds(0))) {
                            adjacentSlotToLeft = true;
                        }
                        if (task.model.to.seconds(0).milliseconds(0).isSame($scope.selectedSlots[slotIndex].model.from.seconds(0).milliseconds(0))) {
                            adjacentSlotToRight = true;
                        }
                    }

                    if (sameSlot && adjacentSlotToLeft && adjacentSlotToRight) {
                        // Trying to delete a slot by clicking on it but it is in the middle of adjacent slots so would create a gap
                        return slotEnum.SAMESLOTMIDDLE;
                    } else if (sameSlot && !(adjacentSlotToLeft && adjacentSlotToRight)) {
                        // Trying to delete a slot and slot is on the end it does not have a selected slot on its left and right
                        return slotEnum.SAMESLOT;
                    } else if (adjacentSlotToLeft || adjacentSlotToRight) {
                        // Slots exist in selected slots but they are not adjacent to the current slot
                        return slotEnum.ADJACENT;
                    } else {
                        return slotEnum.NOTADJACENT;
                    }
                }

                // Slot is the only slot so can be treated as adjacent
                return slotEnum.ADJACENT;
            };

            var clearSelectedSlots = function () {
                $scope.selectedSlots = [];
                $(".selectedSlot").removeClass("selectedSlot");
            };

            $scope.registerEventFunctions = function (eventFunctions) {
                eventFunctions.directives.on.new($scope, function (dName, dScope, dElement, dAttrs, dController) {
                    if (dName === 'ganttTask') {
                        dElement.bind('click', function (event) {
                            var isSlotAdjacent = slotAdjacent(dScope.task);
                            if (isSlotAdjacent == slotEnum.ADJACENT) {                  // A new slot which is adjacent to an existing slot
                                $scope.selectedSlots.push(dScope.task);
                                $(this).addClass("selectedSlot");
                            } else if (isSlotAdjacent == slotEnum.SAMESLOT) {           // A currently selected end slot can be removed from selected slots leaving a block of adjacent slots
                                var oldSelectedSlots = $scope.selectedSlots;
                                $scope.selectedSlots = [];
                                for (var slotIndex = 0; slotIndex < oldSelectedSlots.length; slotIndex++) {
                                    if (oldSelectedSlots[slotIndex].model.id != dScope.task.model.id) {
                                        $scope.selectedSlots.push(oldSelectedSlots[slotIndex]);
                                    }
                                }
                                $(this).removeClass("selectedSlot");
                            } else if (isSlotAdjacent == slotEnum.SAMESLOTMIDDLE) {     // A currently selected slot in the middle of a block so remove all selected slots
                                clearSelectedSlots();
                            } else {                                                    // New slot not adjacent to the currently selected slots
                                clearSelectedSlots();
                                $(this).addClass("selectedSlot");
                                $scope.selectedSlots.push(dScope.task);
                            }
                            $(".removeSelectedSlot").removeClass("removeSelectedSlot");
                        });

                        dElement.bind('mouseenter', function (event) {
                            var isAnAdjacentSlot = slotAdjacent(dScope.task);
                            if (isAnAdjacentSlot == slotEnum.NOTADJACENT ||
                                    isAnAdjacentSlot == slotEnum.SAMESLOTMIDDLE) {
                                $(".selectedSlot").addClass("removeSelectedSlot");
                            } else if (isAnAdjacentSlot == slotEnum.SAMESLOT) {
                                $(this).addClass("removeSelectedSlot");
                            }
                        });

                        dElement.bind('mouseleave', function (event) {
                            $(".selectedSlot").removeClass("removeSelectedSlot");
                        });
                    }
                });

            };

        });

function getDayFromDate(date) {
    var weekday = new Array(7);
    weekday[0] = "Sunday";
    weekday[1] = "Monday";
    weekday[2] = "Tuesday";
    weekday[3] = "Wednesday";
    weekday[4] = "Thursday";
    weekday[5] = "Friday";
    weekday[6] = "Saturday";

    return weekday[date.getDay()];
}

