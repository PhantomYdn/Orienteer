/**
 * Static class which contains utility methods.
 */
var OArchitectUtil = {
    createOClassVertex: function (oClass, x, y) {
        if (x === undefined) x = 0;
        if (y === undefined) y = 0;
        var vertex = new mxCell(oClass,
            new mxGeometry(x, y, OArchitectConstants.OCLASS_WIDTH, OArchitectConstants.OCLASS_HEIGHT));
        vertex.setVertex(true);
        if (!oClass.existsInDb) vertex.setStyle(OArchitectConstants.OCLASS_STYLE);
        else vertex.setStyle(OArchitectConstants.OCLASS_EXISTS_STYLE);
        return vertex;
    },

    createOPropertyVertex: function (property) {
        var vertex = new mxCell(property,
            new mxGeometry(0, 0, 0, OArchitectConstants.OPROPERTY_HEIGHT));
        vertex.setVertex(true);
        if (property.existsInDb) {
            vertex.setStyle(OArchitectConstants.OPROPERTY_EXISTS_STYLE);
        } else vertex.setStyle(OArchitectConstants.OPROPERTY_STYLE);
        return vertex;
    },

    deleteCells: function (cells, withoutChecks) {

        deleteCells(app.editor.graph, getCellsForRemove(cells));

        function deleteCells(graph, cells) {
            graph.getModel().beginUpdate();
            try {
                graph.removeCells(cells, true);
            } finally {
                graph.getModel().endUpdate();
            }
        }

        function getCellsForRemove(cells) {
            var result = [];
            OArchitectUtil.forEach(cells, function (cell) {
                if (cell.edge && cell.source != null) {
                    if (cell.source.value instanceof OArchitectOProperty) {
                        if (cell.source.value.canDisconnect() || withoutChecks) result.push(cell);
                    } else result.push(cell);
                } else result.push(cell);
            });
            return result;
        }
    },

    getOClassesAsJSON: function (graph) {
        var withParents = [];
        var withoutParents = [];
        var cells = graph.getChildVertices(graph.getDefaultParent());

        OArchitectUtil.forEach(cells, function (cell) {
            var oClass = cell.value;
            if (oClass.isSubClass()) withParents.push(oClass.toJson());
            else withoutParents.push(oClass.toJson());
        });

        return '[' + withoutParents.concat(withParents).join(',') + ']';
    },

    getEditorXmlNode: function (graph) {
        var encoder = new mxCodec();
        return encoder.encode(graph.getModel());
    },

    getAllClassCells: function () {
        var graph = app.editor.graph;
        return graph.getChildVertices(graph.getDefaultParent());
    },

    getAllClassesInEditor: function () {
        var cells = OArchitectUtil.getAllClassCells();
        var classes = [];
        OArchitectUtil.forEach(cells, function (cell) {
            classes.push(cell.value);
        });
        return classes;
    },

    toClassNames: function (classes) {
        var names = [];
        OArchitectUtil.forEach(classes, function (oClass) {
            names.push(oClass.name);
        });
        return names;
    },

    getAllClassNames: function () {
        return OArchitectUtil.toClassNames(OArchitectUtil.getAllClassesInEditor());
    },

    manageEdgesBetweenCells: function (sourceCell, targetCell, connect, removeEdgesBetween) {
        var graph = app.editor.graph;
        var cell = null;
        var edgesBetween = graph.getEdgesBetween(sourceCell, targetCell);
        graph.getModel().beginUpdate();
        if (edgesBetween.length > 0 && (!connect || removeEdgesBetween)) {
            removeEdges(edgesBetween);
            edgesBetween = [];
        }

        if (connect && edgesBetween.length === 0) {
            graph.connectionHandler.connect(sourceCell, targetCell);
            cell = graph.getEdgesBetween(sourceCell, targetCell)[0];
        } else if (edgesBetween.length > 0) {
            cell = edgesBetween[0];
            graph.addCell(cell, graph.getDefaultParent());
        }
        graph.getModel().endUpdate();

        function removeEdges(edges) {
            graph.removeCells(edges, true);
        }
        return cell;
    },

    removeCell: function (cell, includeEdges) {
        var graph = app.editor.graph;
        graph.getModel().beginUpdate();
        try {
            graph.removeCells([cell], includeEdges);
        } finally {
            graph.getModel().endUpdate();
        }
    },

    isCellDeletable: function (cell) {
        if (cell == null) return false;
        if (cell.isEdge() && cell.source != null) {
            if (cell.source.value instanceof OArchitectOClass) {
                return !cell.source.value.existsInDb || !cell.target.value.existsInDb
                    || cell.value === OArchitectConstants.UNSAVED_INHERITANCE;
            } else if (cell.source.value instanceof OArchitectOProperty) {
                return !cell.value.existsInDb;
            }
        }
        return true;
    },

    getAllEdgesWithValue: function (value) {
        var edges = [];
        var graph = app.editor.graph;
        OArchitectUtil.forEach(graph.getModel().getChildEdges(graph.getDefaultParent()), function (edge) {
            if (edge.value === value)
                edges.push(edge);
        });
        return edges;
    },

    isValidPropertyTarget: function (cell) {
        var valid = cell.value instanceof OArchitectOClass;
        if (!valid && cell.value instanceof OArchitectOProperty) {
            var classCell = OArchitectUtil.getClassCellByPropertyCell(cell);
            valid = classCell instanceof OArchitectOClass;
        }
        return valid;
    },

    getPropertyCellByName: function (name, oClass) {
        var result = null;
        OArchitectUtil.forEach(this.getClassPropertiesCells(oClass), function (cell, trigger) {
            if (cell.value.name === name) {
                result = cell;
                trigger.stop = true;
            }
        });
        return result;
    },

    getCellByClassName: function (className) {
        var result = null;
        var cells = OArchitectUtil.getAllClassCells();
        OArchitectUtil.forEach(cells, function (cell, trigger) {
            if (cell.value.name === className) {
                result = cell;
                trigger.stop = true;
            }
        });
        return result;
    },

    getClassPropertiesCells: function (oClass) {
        var graph = app.editor.graph;
        var cells = graph.getChildVertices(oClass.cell);
        var result = [];
        OArchitectUtil.forEach(cells, function (cell) {
            result.push(cell);
            // if (cell.value instanceof OArchitectOProperty) {
            //     if (oClass.getProperty(cell.value.name) != null) {
            //
            //     }
            // }
        });
        return result;
    },

    getClassCellByPropertyCell: function (cell) {
        var graph = app.editor.graph;
        if (cell.value instanceof OArchitectOClass)
            return cell;
        if (cell === graph.getDefaultParent())
            return null;
        return this.getClassCellByPropertyCell(graph.getModel().getParent(cell));
    },

    getClassFromJson: function (json) {
        var result = null;
        if (json != null && json.length > 0) {
            var parse = JSON.parse(json);
            var cell = this.getCellByClassName(parse.name);

            if (cell != null) {
                result = cell.value;
            } else {
                result = new OArchitectOClass();
                result.configFromJson(parse);
            }
        }
        return result;
    },

    getPropertyWithMinOrder: function (properties) {
        var property = properties.length > 0 ? getOrder(properties) : 0;
        for (var i = 0; i < properties.length; i++) {
            if (OArchitectUtil.isOrderValidProperty(properties[i])) {
                if (property > properties[i].getOrder()) {
                    property = properties[i].getOrder();
                }
            }
        }

        function getOrder(properties) {
            for (var i = 0; i < properties.length; i++) {
                if (OArchitectUtil.isOrderValidProperty(properties[i]))
                    return properties[i].getOrder();
            }
            return 0;
        }

        return property;
    },

    getOrderValidProperties: function (properties) {
        var result = [];
        OArchitectUtil.forEach(properties, function (property) {
            if (OArchitectUtil.isOrderValidProperty(property)) {
                result.push(property);
            }
        });
        return result;
    },

    isOrderValidProperty: function (property) {
        return !property.isSubClassProperty() || !property.isSuperClassExistsInEditor();
    },

    getPropertyFromJson: function (json) {
        var result = null;
        if (json != null && json.length > 0) {
            var parse = JSON.parse(json);
            var classCell = this.getCellByClassName(parse.ownerClass);

            if (classCell != null) {
                var ownerClass = classCell.value;
                result = ownerClass.getProperty(parse.name);
            }
        }
        return result;
    },

    existsOClassInGraph: function (graph, className) {
        var exists = false;
        var cells = graph.getChildVertices(graph.getDefaultParent());
        OArchitectUtil.forEach(cells, function (cell, trigger) {
            if (cell.value.name.toUpperCase() === className.toUpperCase()) {
                exists = true;
                trigger.stop = true;
            }
        });
        return exists;
    },

    forEach: function (arr, func) {
        if (arr != null && arr.length > 0 && func != null) {
            var trigger = {
                stop: false
            };
            for (var i = 0; i < arr.length; i++) {
                func(arr[i], trigger);
                if (trigger.stop) break;
            }
        }
    },

    /**
     * Creates function for save {@link OArchitectOClass} and {@link OArchitectOProperty} to editor xml config.
     * Overrides {@link mxObjectCodec#writeComplexAttribute}
     * @returns Function
     */
    createWriteComplexAttributeFunction: function () {
        var defaultBehavior = mxObjectCodec.prototype.writeComplexAttribute;
        return function (enc, obj, name, value, node) {
            if (value instanceof OArchitectEditorObject) {
                value = value.toEditorConfigObject();
            } else if (name === 'cell' || name === 'configuredFromEditorConfig' || name === 'existsInEditor') {
                value = undefined;
            }

            defaultBehavior.apply(this, arguments);
        };
    },

    /**
     * Create function for decode {@link OArchitectOClass} and {@link OArchitectOProperty} from editor xml config.
     * Overrides {@link mxCodec#decode}
     * @returns Function
     */
    createDecodeFunction: function () {
        var defaultBehavior = mxCodec.prototype.decode;
        
        return function (node, into) {
            var result = defaultBehavior.apply(this, arguments);
            if (into instanceof mxGraphModel) {
                var graph = app.editor.graph;
                var classCells = graph.getChildVertices(graph.getDefaultParent());
                var classes = [];
                OArchitectUtil.forEach(classCells, function (classCell) {
                    var oClass = new OArchitectOClass();
                    oClass.configFromJson(JSON.parse(classCell.value.json), classCell);
                    oClass.previousState = null;
                    oClass.nextState = null;
                    classes.push(oClass);
                });
                OArchitectUtil.updateExistsInDB(classes);
            }
            return result;
        }
    },

    updateAllCells: function () {
        var graph = app.editor.graph;
        graph.getModel().beginUpdate();
        OArchitectUtil.forEach(graph.getChildCells(graph.getDefaultParent()), function (cell) {
            graph.getModel().setValue(cell, cell.value);
        });
        graph.getModel().endUpdate();
    },

    updateExistsInDB: function(classes) {
        OArchitectUtil.forEach(classes, function (oClass) {
            oClass.setExistsInDb(oClass.existsInDb);
            OArchitectUtil.forEach(oClass.properties, function (property) {
                property.setExistsInDb(property.existsInDb);
            });
        });
    },

    // executeRemovePropertyCommands: function (property) {
    //     var model = app.editor.graph.getModel();
    //     model.beginUpdate();
    //     OArchitectConnector.disable = true;
    //     if (property.inverseProperty !== null) {
    //         var inverseProp = property.inverseProperty;
    //         if (property === inverseProp.inverseProperty) {
    //             model.execute(new OPropertyInverseChangeCommand(inverseProp, inverseProp.inversePropertyEnable, inverseProp.inverseProperty))
    //         }
    //         model.execute(new OPropertyInverseChangeCommand(property, property.inversePropertyEnable, inverseProp));
    //         if (inverseProp.linkedClass === property.ownerClass) {
    //             model.execute(new OPropertyLinkChangeCommand(inverseProp, inverseProp.linkedClass));
    //         }
    //     }
    //     if (property.linkedClass !== null) {
    //         model.execute(new OPropertyLinkChangeCommand(property, property.linkedClass));
    //     }
    //     model.execute(new OPropertyCreateCommand(property, property.ownerClass, true));
    //     OArchitectConnector.disable = false;
    //     model.endUpdate();
    //
    // },

    inverseArray: function (arr) {
        var n = arr.length - 1;
        for (var i = 0; i < arr.length; i++) {
            if (i >= n) {
                break;
            }
            var tmp = arr[i];
            arr[i] = arr[n];
            arr[n] = tmp;
            n--;
        }
        return arr;
    }
};

var OArchitectEditorObject = function (property) {
    this.property = property;
};

OArchitectEditorObject.prototype.configFromJson = function () {
    console.log('config editor object from json');
};

OArchitectEditorObject.prototype.toJson = function () {
    return JSON.stringify(this);
};

OArchitectEditorObject.prototype.toEditorConfigObject = function () {
    var json = this.toJson();
    return {
        "json": json
    };
};