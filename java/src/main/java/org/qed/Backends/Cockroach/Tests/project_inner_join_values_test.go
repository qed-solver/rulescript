package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestProjectInnerJoinValues(t *testing.T) {
	rule := loadRule(t, "ProjectInnerJoinValues")
	e := newEnv(t)
	left, lc := e.scan("u", 1)
	r0 := e.f.Metadata().AddColumn("r0", types.Int)
	r1 := e.f.Metadata().AddColumn("r1", types.Int)
	right := e.intValues([]opt.ColumnID{r0, r1}, []int{1, 2})
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(r0)))
	before := e.f.ConstructInnerJoin(left, right, on, &memo.JoinPrivate{})
	project := e.f.ConstructProject(left, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.num(1), r0),
		e.f.ConstructProjectionsItem(e.num(2), r1),
	}, opt.MakeColSet(lc[0]))
	e.expectRewrite(rule, before, e.f.ConstructSelect(project, on))
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(left, right, on, &memo.JoinPrivate{}))
	wrong := e.intValues([]opt.ColumnID{r0, r1}, []int{1, 3})
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(left, wrong, on, &memo.JoinPrivate{}))
}

func TestSQLProjectInnerJoinValues(t *testing.T) {
	expectSQLFires(t, "ProjectInnerJoinValues", "SELECT * FROM (VALUES (9)) AS l(a) JOIN (VALUES (1,2)) AS r(b,c) ON a=b")
}
