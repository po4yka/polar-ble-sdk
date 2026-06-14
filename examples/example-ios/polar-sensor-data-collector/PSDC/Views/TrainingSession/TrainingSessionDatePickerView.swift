/// Copyright © 2025 Polar Electro Oy. All rights reserved.

import Foundation
import SwiftUI

struct TrainingSessionDatePickerView: View {
    
    @EnvironmentObject private var bleSdkManager: PolarBleSdkManager
    
    @Binding var isPresented: Bool
    @Binding var showTrainingSessionView: Bool
    
    @State private var dates: Set<DateComponents> = []
    @State private var startDate: DateComponents? = nil
    @State private var endDate:DateComponents? = nil

    var selectedDates: Binding<Set<DateComponents>> {
        Binding {
            return dates
        } set: { selectedDates in

            startDate = boundaryDateComponents(in: selectedDates, by: <)
            
            if (selectedDates.count > 1) {
                endDate = boundaryDateComponents(in: selectedDates, by: >)
            } else {
                endDate = startDate
            }
            
            if (startDate != nil && endDate != nil) {
                fillDatesBetween(for: startDate!, for: endDate!)
            }
        }
    }

    @State private var formattedDates: String = ""
    let formatter = DateFormatter()
    
    var body: some View {
        Group {
            Group {
                VStack {
                    
                    MultiDatePicker("", selection: selectedDates)
                        .frame(height: 300)
                    
                    Button(action: {
                        isPresented = false
                        Task { @MainActor in
                            if (!dates.isEmpty) {
                                var calendar = Calendar.current
                                calendar.timeZone = TimeZone(abbreviation: "UTC")!
                                let startDate = calendar.date(from: boundaryDateComponents(in: dates, by: <)!)!
                                let endDate = calendar.date(from: boundaryDateComponents(in: dates, by: >)!)!
                                bleSdkManager.trainingSessionData.startTime = startDate
                                bleSdkManager.trainingSessionData.endTime = endDate
                                showTrainingSessionView = true
                                isPresented = false
                                await bleSdkManager.listTrainingSessions(start: startDate, end: endDate)
                            } else {
                                bleSdkManager.trainingSessionData.loadState =  TrainingSessionDataLoadingState.failed(error: "No date selected!")
                                showTrainingSessionView = false
                            }
                        }
                    }, label: {
                        Text("Done").font(.title3)
                    }
                    ).padding(.vertical, 15)
                     .disabled(startDate == nil || endDate == nil)
                    
                    Button(action: {
                        isPresented = false
                    }, label: {
                        Text("Cancel")
                            .font(.title3)
                            .foregroundStyle(.red)
                    }).padding(.vertical, 10)
                }
            }
        }
    }
    
    private func fillDatesBetween(for startDate: DateComponents, for endDate: DateComponents) {
        let calendar = Calendar.current
        var currentDate = startDate
        dates = []
        dates.insert(startDate)
        while let current = calendar.date(from: currentDate),
              let end = calendar.date(from: endDate),
              current < end {
            let date: Date = current
            if let dateOfDay = calendar.date(byAdding: .day, value: 1, to: date) {
                currentDate = calendar.dateComponents([.year, .month, .day], from: dateOfDay)
                let components = calendar.dateComponents([.year, .month, .day], from: dateOfDay)
                dates.insert(components)
            }
        }
    }

    private func boundaryDateComponents(in dates: Set<DateComponents>, by areInIncreasingOrder: (Date, Date) -> Bool) -> DateComponents? {
        let calendar = Calendar.current
        return dates
            .compactMap { components -> (DateComponents, Date)? in
                guard let date = calendar.date(from: components) else { return nil }
                return (components, date)
            }
            .sorted { lhs, rhs in areInIncreasingOrder(lhs.1, rhs.1) }
            .first?
            .0
    }
}
