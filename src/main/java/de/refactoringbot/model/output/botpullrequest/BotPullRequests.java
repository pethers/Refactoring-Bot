package de.refactoringbot.model.output.botpullrequest;

import java.util.ArrayList;
import java.util.List;

public class BotPullRequests {

	List<BotPullRequest> allPullRequests = new ArrayList<>();

	public List<BotPullRequest> getAllPullRequests() {
		return allPullRequests;
	}

	public void setAllPullRequests(List<BotPullRequest> allPullRequests) {
		this.allPullRequests = allPullRequests;
	}
	
	public void addPullRequest(BotPullRequest pullRequest) {
		this.allPullRequests.add(pullRequest);
	}
	
	public void removePullRequest(BotPullRequest pullRequest) {
		this.allPullRequests.remove(pullRequest);
	}
	
}
