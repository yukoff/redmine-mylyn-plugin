package net.sf.redmine_mylyn.internal.ui.editor;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import net.sf.redmine_mylyn.api.model.Configuration;
import net.sf.redmine_mylyn.api.model.CustomField;
import net.sf.redmine_mylyn.api.model.Project;
import net.sf.redmine_mylyn.core.IRedmineConstants;
import net.sf.redmine_mylyn.core.RedmineAttribute;
import net.sf.redmine_mylyn.core.RedmineUtil;

import org.eclipse.mylyn.tasks.core.data.TaskAttribute;
import org.eclipse.mylyn.tasks.core.data.TaskData;

public class TaskDataValidator {

	private final Configuration configuration;
	
	public TaskDataValidator(Configuration configuration) {
		this.configuration = configuration;
	}
	
	public ErrorMessageCollector validateTaskData(TaskData taskData) {
		ErrorMessageCollector result = new ErrorMessageCollector();
		validateDefaultAttributes(taskData, result);
		validateCustomAttributes(taskData, result);
		return result;
	}
	
	public ErrorMessageCollector validateTaskAttribute(TaskData taskData, TaskAttribute attribute) {
		ErrorMessageCollector collector = new ErrorMessageCollector();
		if(attribute.getId().startsWith(IRedmineConstants.TASK_KEY_PREFIX_ISSUE_CF)) {
			String customFieldId = attribute.getId().substring(IRedmineConstants.TASK_KEY_PREFIX_ISSUE_CF.length());
			CustomField customField = configuration.getCustomFields().getById(RedmineUtil.parseIntegerId(customFieldId));
			if(customField!=null) {
				validateCustomAttribute(attribute, customField, collector);
			}
		} else if (attribute.getId().equals(RedmineAttribute.ESTIMATED.getTaskKey())) {
			validateEstimatedHours(taskData, collector);
		} else if (attribute.getId().equals(RedmineAttribute.PARENT.getTaskKey())) {
			validateParentTask(taskData, collector);
		}
		
		return collector;
	}
	
	protected void validateDefaultAttributes(TaskData taskData, ErrorMessageCollector collector) {
		validateRequiredDefaultAttributes(taskData, collector);
		validateEstimatedHours(taskData, collector);
	}

	protected void validateRequiredDefaultAttributes(TaskData taskData, ErrorMessageCollector collector) {
		TaskAttribute rootAttr = taskData.getRoot();
		TaskAttribute taskAttr = null;
		
		for (RedmineAttribute redmineAttribute : RedmineAttribute.values()) {
			if (redmineAttribute.isRequired()) {
				if(redmineAttribute==RedmineAttribute.STATUS && taskData.isNew())  {
					redmineAttribute=RedmineAttribute.STATUS_CHG;
				}
				
				taskAttr = rootAttr.getAttribute(redmineAttribute.getTaskKey());
				if (taskAttr==null || taskAttr.getValue().trim().isEmpty()) {
					collector.add(taskAttr, String.format("%s is required", redmineAttribute.getLabel()));
				}
			}
		}
	}
	
	protected void validateEstimatedHours(TaskData taskData, ErrorMessageCollector collector) {
		TaskAttribute attribute = taskData.getRoot().getAttribute(RedmineAttribute.ESTIMATED.getTaskKey());
		if (attribute != null) {
			
			if (!attribute.getValue().trim().isEmpty()) {
				try {
					Double.valueOf(attribute.getValue().trim());
				} catch (NumberFormatException e) {
					collector.add(attribute, RedmineAttribute.ESTIMATED.getLabel() + " must be a float");
				}
			}
		}
	}
	
	protected void validateParentTask(TaskData taskData, ErrorMessageCollector collector) {
		TaskAttribute attribute = taskData.getRoot().getAttribute(RedmineAttribute.PARENT.getTaskKey());
		if (attribute != null) {
			if (!attribute.getValue().trim().matches("^\\d*$")) {
				collector.add(attribute, RedmineAttribute.PARENT.getLabel() + " must be single Task ID");
			}
		}
	}

	protected void validateCustomAttributes(TaskData taskData, ErrorMessageCollector collector) {
		TaskAttribute rootAttribute = taskData.getRoot();
		int projectId = RedmineUtil.parseIntegerId(rootAttribute.getAttribute(RedmineAttribute.PROJECT.getTaskKey()).getValue());
		int trackerId = RedmineUtil.parseIntegerId(rootAttribute.getAttribute(RedmineAttribute.TRACKER.getTaskKey()).getValue());
		
		Project project = configuration.getProjects().getById(projectId);
		int[] customFieldIds = project.getCustomFieldIdsByTrackerId(trackerId);
		
		if(customFieldIds!=null) {
			for (int customFieldId : customFieldIds) {
				CustomField customField = configuration.getCustomFields().getById(customFieldId);
				TaskAttribute attribute = rootAttribute.getAttribute(IRedmineConstants.TASK_KEY_PREFIX_ISSUE_CF + customFieldId);
				if(customField!=null && attribute!=null) {
					validateCustomAttribute(attribute, customField, collector);
				}
			}
		}
	}
	
	protected void validateCustomAttribute(TaskAttribute taskAttribute, CustomField customField, ErrorMessageCollector collector) {
		String value = taskAttribute.getValue();
		
		if(customField.isRequired() && value.trim().isEmpty()) {
			collector.add(taskAttribute, String.format("%s is required", customField.getLabel()));
			return;
		}
		
		if(!value.isEmpty()) {
			if(!validateCustomAttributeType(value, customField)) {
				collector.add(taskAttribute, String.format("%s must be a %s", customField.getLabel(), customField.getFieldFormat().getLabel()));
				return;
			}
			
			int max = customField.getMaxLength(); 
			if (max>0 && max<value.length()) {
				collector.add(taskAttribute, String.format("%s: maximum length of %d exceeded", customField.getLabel(), customField.getMaxLength()));
				return;
			}
			
			int min = customField.getMinLength();
			if (min>0 && min>value.length()) {
				collector.add(taskAttribute, String.format("%s: minimum length of %d below", customField.getLabel(), customField.getMinLength()));
				return;
			} 
			
			String pattern = customField.getRegexp();
			if (pattern!=null && !pattern.isEmpty() && !Pattern.matches(pattern, value)) {
				collector.add(taskAttribute, String.format("%s: dosn't match %s", customField.getLabel(), customField.getRegexp()));
				return;
			}
		}
	}
	
	protected boolean validateCustomAttributeType(String value, CustomField customField) {
		try {
			switch (customField.getFieldFormat()) {
			case FLOAT: Double.valueOf(value); break;
			case INT: Integer.valueOf(value); break;
			}
		} catch (NumberFormatException e) {
			return false;
		}
		return true;
	}
	
	public class ErrorMessageCollector {
		private String firstMessage;
		private Map<String, String> errorMessages;
		
		void add(TaskAttribute taskAttribute, String errorMessage) {
			if(errorMessages==null) {
				firstMessage = errorMessage;
				errorMessages = new HashMap<String, String>();
			}
			errorMessages.put(taskAttribute.getId(), errorMessage);
		}
		
		public boolean hasErrors() {
			return errorMessages!=null;
		}
		
		public String getFirstErrorMessage() {
			return firstMessage;
		}
	}
	
}
