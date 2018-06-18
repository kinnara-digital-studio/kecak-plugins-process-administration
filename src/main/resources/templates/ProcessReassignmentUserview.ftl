<link rel="stylesheet" type="text/css" href="${request.contextPath}/plugin/${className}/css/jquery.dataTables.min.css"/>
<link rel='stylesheet' type='text/css' href='${request.contextPath}/plugin/${className}/css/bootstrap.min.css' />
<link href="//netdna.bootstrapcdn.com/bootstrap/3.0.0/css/bootstrap-glyphicons.css" rel="stylesheet">
<script type="text/javascript" src="${request.contextPath}/plugin/${className}/js/jquery.dataTables.min.js"></script>
<script src='${request.contextPath}/plugin/${className}/js/multiselect.min.js'></script>
<script src='${request.contextPath}/plugin/${className}/js/bootstrap.min.js'></script>
<script type="text/javascript">
var arrId = '';
var activityId = '';
function call() {
    var objectProcessId='';
    var dummiArrId;
    var arrProcessId = $('#processMonitoring').DataTable().rows('.selected').data();
    $.each(arrProcessId, function(index, value) {
            dummiArrId=value[6];
            activityId = value[7];
    });
    arrId = dummiArrId.replace(/ /g,'');
    var arrIdSplit = arrId.split(',');
    var count = arrIdSplit.length;

    for (var i = 0; i < count; i++) {
        var trimId = arrIdSplit[i].trim();
        $('#multiselect_to').append($('<option>', {
            value: trimId,
            text: trimId+' '
        }));
        $("#multiselect option[value='"+trimId+"']").remove();
    };
};
$(document).ready(function(){
    var table = $('#processMonitoring').DataTable({
                    "columnDefs": [
                        {"targets": [6],"visible": false},
                        {"targets": [7],"visible": false}
                    ]
                });

    $('#processMonitoring tbody').on('click', 'tr', function () {
        var data = table.row( this ).data();
        if ( $(this).hasClass('selected') ) {
            $(this).removeClass('selected');
        }else {
            table.$('tr.selected').removeClass('selected');
            $(this).addClass('selected');
        }
    } );

    $('#btnHide').click(function() {
        if ($('#tableReassignment').is(':visible') && table.rows('.selected').data().length >= 1) {
            $('#tableReassignment').hide();
        } else {
            if ($('.selected').length > 0) {
                    $('#tableReassignment').show();
                    call();
            } else {
                    alert('Please select a task first');
            }
        }
    });

    $('#multiselect').multiselect({
        search: {
            left: '<input type="text" name="q" class="form-control" placeholder="Search..." />',
            right: '<input type="text" name="q" class="form-control" placeholder="Search..." />',
        },
        keepRenderingSort: true
    });
    $('.js-multiselect').multiselect({
        right: '#js_multiselect_to_1',
        rightAll: '#js_right_All_1',
        rightSelected: '#js_right_Selected_1',
        leftSelected: '#js_left_Selected_1',
        leftAll: '#js_left_All_1'
    });
    $('#keepRenderingSort').multiselect({
        keepRenderingSort: true
    });
});

function post() {
	var url ='';
	var reassignment = '';
	var dummiReassignment = $('#multiselect_to').text();
	var reassignmentUser = dummiReassignment.split(' ');
	var dummiReassignmentUser='';
	
	for (i = 0, len = reassignmentUser.length -1; i < len; i++){
		dummiReassignmentUser = reassignmentUser[i].trim()+',';
	};
	reassignment = dummiReassignmentUser.slice(0,-1);
	reassignment = reassignment.split(',');
	var countReassignment = reassignment.length;
	var arrIdSplit = arrId.split(',');
	var countArrId = arrIdSplit.length;
	if(countReassignment > countArrId){
		alert('Reassignment User More Than Actual User List')
	}else if(countReassignment < countArrId){
		alert('Reassignment User Less Than Actual User List');
	}else{
		for (var i = 0; i < countArrId; i++) {
                        //var historyTableName = $("#historyTableName").val();
                        //if(historyTableName!=""){
                            //Call Web service to Store to history Table
                        //    var refFieldHist = $("#refFieldHist").val();
                            
                        //    url='';
                        //    url = window.location.protocol + "//"+  window.location.host;
                        //    url += '${request.contextPath}/web/json/plugin/${className}/service?';
                        //    url += 'historyTableName='+  historyTableName+'&refFieldHist='+refFieldHist+'&value='+; //dapetin valuenya dari nilai yg dipilih
                        //    $.ajax({
                        //            type: "POST",
                        //            url: url,
                        //            success: function(msg){
                                        //location.reload();
                        //            }
                        //    });
                        //}

			url='';
			url = window.location.protocol + "//"+  window.location.host;
			url += '${request.contextPath}/web/json/monitoring/activity/reassign?activityId='+  activityId;
			url += '&username='+  reassignment[i]  +'&replaceUser='+  arrIdSplit[i] +'&j_username=${masterUser}&hash=${masterHash}&loginAs=${loginAs}';
			$.ajax({
				type: "POST",
				url: url,
				success: function(msg){
				location.reload();
				}
			});
		};
	};
};
</script>
<div class="processMonitor-body-content">
    <table id="processMonitoring" class="table table-striped table-bordered" cellspacing="0" width="100%">
        <thead>
            <tr>
                <#list headers as header>
                <th>${header}</th>
                </#list>
            </tr>
        </thead>
        <tbody>
             <#list datas as data>
            <tr>
                <td>${data.processId}</td>
                <td>${data.processName}</td>
                <td>${data.requester}</td>
                <td>${data.activityName}</td>
                <td>${data.assignee}</td>
                <td>${data.referenceField}</td>
                <td>${data.assigneeUsername}</td>
                <td>${data.activityId}</td>
            </tr>
            </#list>
        </tbody>
    </table>
    <button type="button" id='btnHide' class="btn btn-primary">Reassignment</button>
    <br>
    <div id='tableReassignment' class='row' style='display: none;'>
        <div class='col-xs-5'>
            <select name='from[]' id='multiselect' class='multiselect form-control' size='13' multiple='multiple'>
                ${replaceUser}
            </select>
        </div>
        <div class='col-xs-2'>
            <button type="button" id="multiselect_undo" class="btn btn-primary btn-block">undo</button>
            <button type="button" id="multiselect_redo" class="btn btn-warning btn-block">redo</button>
            <button type='button' id='multiselect_rightAll' class='btn btn-block'><i class='glyphicon glyphicon-forward'></i></button>
            <button type='button' id='multiselect_rightSelected' class='btn btn-block'><i class='glyphicon glyphicon-chevron-right'></i></button>
            <button type='button' id='multiselect_leftSelected' class='btn btn-block'><i class='glyphicon glyphicon-chevron-left'></i></button>
            <button type='button' id='multiselect_leftAll' class='btn btn-block'><i class='glyphicon glyphicon-backward'></i></button>
            <button type='button' id='submid' class='btn btn-block' onClick='post()'>Submit</button>
        </div>
        <div class='col-xs-5'>
            <select name='to[]' id='multiselect_to' class='form-control' size='13' multiple='multiple'></select>
            <input type="hidden" id="historyTableName" name="historyTableName" value="${historyTableName}"/>
            <input type="hidden" id="refFieldHist" name="refFieldHist" value="${refFieldHist}"/>
        </div>
    </div>
</div>
<div style="clear:both;"></div>