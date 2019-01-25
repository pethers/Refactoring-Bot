package de.refactoringbot.services.wit;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.refactoringbot.api.wit.WitDataGrabber;
import de.refactoringbot.model.botissue.BotIssue;
import de.refactoringbot.model.configuration.GitConfiguration;
import de.refactoringbot.model.exceptions.CommentUnderstandingMessage;
import de.refactoringbot.model.exceptions.WitAPIException;
import de.refactoringbot.model.output.botpullrequestcomment.BotPullRequestComment;
import de.refactoringbot.model.wit.JavaAnnotation;
import de.refactoringbot.model.wit.RefactoringObject;
import de.refactoringbot.model.wit.RefactoringOperation;
import de.refactoringbot.model.wit.RefactoringString;
import de.refactoringbot.model.wit.WitObject;
import de.refactoringbot.refactoring.RefactoringOperations;
import de.refactoringbot.services.main.FileService;

/**
 * This class implements functions around the wit.ai service.
 * 
 * @author Stefan Basaric
 *
 */
@Service
public class WitService {

	private FileService fileService;
	private WitDataGrabber witDataGrabber;

	private static final Logger logger = LoggerFactory.getLogger(WitService.class);

	@Autowired
	public WitService(FileService fileService, WitDataGrabber witDataGrabber) {
		this.fileService = fileService;
		this.witDataGrabber = witDataGrabber;
	}

	/**
	 * 
	 * @param comment
	 * @param gitConfig
	 * @return
	 * @throws Exception
	 */
	public BotIssue createBotIssue(BotPullRequestComment comment, GitConfiguration gitConfig) throws Exception {
		try {
			// Create object
			BotIssue issue = new BotIssue();

			// Add data to comment
			issue.setCommentServiceID(comment.getCommentID().toString());
			issue.setLine(comment.getPosition());
			issue.setFilePath(comment.getFilepath());

			// Set all Java-Files and Java-Roots
			List<String> allJavaFiles = fileService.getAllJavaFiles(gitConfig.getRepoFolder());
			issue.setAllJavaFiles(allJavaFiles);
			issue.setJavaRoots(fileService.findJavaRoots(allJavaFiles));

			mapCommentBodyToIssue(issue, comment.getCommentBody());
			return issue;
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			throw new Exception("Could not create a BotIssue from the comment '" + comment.getCommentBody() + "'!");
		}
	}

	/**
	 * This method maps the information of a comment to the issue.
	 * 
	 * @param issue
	 * @param commentBody
	 * @throws WitAPIException
	 * @throws CommentUnderstandingMessage
	 */
	private void mapCommentBodyToIssue(BotIssue issue, String commentBody)
			throws WitAPIException, CommentUnderstandingMessage {
		// Get Wit-Object from Wit-API
		WitObject witObject = witDataGrabber.getWitObjectFromMessage(commentBody);

		// If wit returns multiple operations or multiple objects (or 0)
		if (witObject.getEntities().getRefactoringObject().size() != 1
				|| witObject.getEntities().getRefactoringOperation().size() != 1) {
			throw new CommentUnderstandingMessage("Not sure what operation to execute on what object!");
		}

		// Read unique operation + object
		RefactoringOperation refOp = witObject.getEntities().getRefactoringOperation().get(0);
		RefactoringObject refObj = witObject.getEntities().getRefactoringObject().get(0);

		// If Add-Annotation refactoring is sure
		if (refOp.getValue().equals("add") && refObj.getValue().equals("annotation")) {
			// If 0/2+ possilbe annotations returned
			if (witObject.getEntities().getJavaAnnotations().size() != 1) {
				throw new CommentUnderstandingMessage("Not sure which annotation you want to add!");
			}
			// Read unique java annotation
			JavaAnnotation javaAnnot = witObject.getEntities().getJavaAnnotations().get(0);
			// If override annotation returned
			if (javaAnnot.getValue().equals("Override")) {
				issue.setRefactoringOperation(RefactoringOperations.ADD_OVERRIDE_ANNOTATION);
				// If some unknown annotation returned
			} else {
				throw new CommentUnderstandingMessage("Adding '" + javaAnnot.getValue() + "' is not supported!");
			}
		} else {
			throw new CommentUnderstandingMessage("Can not '" + refOp + "' an '" + refObj + "'!");
		}

		// If Reorder-Modifier
		if (refOp.getValue().equals("reorder") && refObj.getValue().equals("modifier")) {
			// Annotations or refactoring strings are not involved
			if (witObject.getEntities().getRefactoringString().size() != 0
					|| witObject.getEntities().getJavaAnnotations().size() != 0) {
				logger.warn(
						"Wit detected an 'reorder string' and/or 'annotation' on a 'reorder modifier' refactoring! Comment: "
								+ commentBody);
			}
			issue.setRefactoringOperation(RefactoringOperations.REORDER_MODIFIER);
		} else {
			throw new CommentUnderstandingMessage("Can not '" + refOp + "' an '" + refObj + "'!");
		}

		// If rename method
		if (refOp.getValue().equals("rename") && refObj.getValue().equals("method")) {
			// If new method name is uncertain
			if (witObject.getEntities().getRefactoringString().size() != 1) {
				throw new CommentUnderstandingMessage("Not sure what the new method name is!");
			}
			// Annotations are not involved
			if (witObject.getEntities().getJavaAnnotations().size() != 0) {
				logger.warn("Wit detected an 'annotation' on a 'rename method' refactoring! Comment: " + commentBody);
			}
			// Read unique refactoring string (method name object)
			RefactoringString refStr = witObject.getEntities().getRefactoringString().get(0);
			issue.setRefactoringOperation(RefactoringOperations.RENAME_METHOD);
			issue.setRefactorString(refStr.getValue());
		} else {
			throw new CommentUnderstandingMessage("Can not '" + refOp + "' an '" + refObj + "'!");
		}

		// If remove parameter
		if (refOp.getValue().equals("remove") && refObj.getValue().equals("parameter")) {
			// If parameter name is uncertain
			if (witObject.getEntities().getRefactoringString().size() != 1) {
				throw new CommentUnderstandingMessage("Not sure what the parameter name is!");
			}
			// Annotations are not involved
			if (witObject.getEntities().getJavaAnnotations().size() != 0) {
				logger.warn(
						"Wit detected an 'annotation' on a 'remove parameter' refactoring! Comment: " + commentBody);
			}
			// Read unique refactoring string (parameter name object)
			RefactoringString refStr = witObject.getEntities().getRefactoringString().get(0);
			issue.setRefactoringOperation(RefactoringOperations.REMOVE_PARAMETER);
			issue.setRefactorString(refStr.getValue());
		} else {
			throw new CommentUnderstandingMessage("Can not '" + refOp + "' an '" + refObj + "'!");
		}
	}
}
