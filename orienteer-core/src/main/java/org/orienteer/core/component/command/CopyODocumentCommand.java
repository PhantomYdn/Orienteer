package org.orienteer.core.component.command;

import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.record.impl.ODocument;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.model.IModel;
import org.orienteer.core.component.property.DisplayMode;
import org.orienteer.core.component.structuretable.OrienteerStructureTable;
import org.orienteer.core.component.table.OrienteerDataTable;
import org.orienteer.core.web.ODocumentPage;
import ru.ydn.wicket.wicketorientdb.model.OClassModel;
import ru.ydn.wicket.wicketorientdb.model.ODocumentModel;
import ru.ydn.wicket.wicketorientdb.security.ISecuredComponent;
import ru.ydn.wicket.wicketorientdb.security.OSecurityHelper;
import ru.ydn.wicket.wicketorientdb.security.OrientPermission;
import ru.ydn.wicket.wicketorientdb.security.RequiredOrientResource;

import java.util.List;

/**
 * {@link Command} for {@link OrienteerStructureTable} to copy {@link ODocument}
 */
public class CopyODocumentCommand extends AbstractCopyCommand<ODocument> implements ISecuredComponent {

    private static final long serialVersionUID = 1L;
    private IModel<OClass> classModel;

    public CopyODocumentCommand(OrienteerDataTable<ODocument, ?> table, OClass oClazz) {
        this(table, new OClassModel(oClazz));
    }

    public CopyODocumentCommand(OrienteerDataTable<ODocument, ?> table, IModel<OClass> classModel) {
        super(table);
        this.classModel = classModel;
        setRequireExactOne(true).setRequireAtLeastOne(true);
    }


    @Override
    protected void performMultiAction(AjaxRequestTarget target, List<ODocument> objects) {
        super.performMultiAction(target, objects);
        getDatabaseSession().commit(true);
        getDatabaseSession().begin();
    }

    @Override
    protected void perfromSingleAction(AjaxRequestTarget target, ODocument object) {
        ODocument copy = getDatabaseSession().newInstance(object.getClassName());
        for (String fieldName : object.fieldNames()) {
            copy.field(fieldName, (Object) object.field(fieldName));
        }

        setResponsePage(new ODocumentPage(new ODocumentModel(copy)).setModeObject(DisplayMode.EDIT));
    }

    @Override
    public RequiredOrientResource[] getRequiredResources() {
        return OSecurityHelper.requireOClass(classModel.getObject(), OrientPermission.CREATE);
    }
}
