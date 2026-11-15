package com.coachingcenter.api.offline;

import static com.coachingcenter.api.offline.OfflineSupport.actorId;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.offline.OfflineResultStudentService.Leaderboard;
import com.coachingcenter.api.offline.OfflineResultStudentService.StudentResults;

/**
 * The results of the student's own admission (results-api-contract, student routes). Each route acts on one of the signed-in student's own
 * admissions: another student's admission id answers 404.
 */
@RestController
public class OfflineResultStudentController {

	private final OfflineResultStudentService results;

	public OfflineResultStudentController(OfflineResultStudentService results) {
		this.results = results;
	}

	@GetMapping("/api/v1/me/admissions/{admissionId}/results")
	public StudentResults results(@PathVariable UUID admissionId) {
		return results.results(actorId(), admissionId);
	}

	@GetMapping("/api/v1/me/admissions/{admissionId}/results/{classId}")
	public Leaderboard leaderboard(@PathVariable UUID admissionId, @PathVariable UUID classId) {
		return results.leaderboard(actorId(), admissionId, classId);
	}

}
