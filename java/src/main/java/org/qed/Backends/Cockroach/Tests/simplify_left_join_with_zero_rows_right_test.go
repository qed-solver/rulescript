package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestSimplifyLeftJoinWithZeroRowsRight(t *testing.T) {
	rule := loadRule(t, "SimplifyLeftJoinWithZeroRowsRight")
	e := newEnv(t)
	left, lc := e.scan("u", 2)
	_, rc := e.scan("t", 2)
	emptyRight := e.empty(rc...)
	before := e.f.ConstructLeftJoin(left, emptyRight, e.filters(), &memo.JoinPrivate{})
	projections := memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructNull(e.f.Metadata().ColumnMeta(rc[0]).Type), rc[0]),
		e.f.ConstructProjectionsItem(e.f.ConstructNull(e.f.Metadata().ColumnMeta(rc[1]).Type), rc[1]),
	}
	want := e.f.ConstructProject(left, projections, opt.MakeColSet(lc...))
	e.expectRewrite(rule, before, want)

	nonemptyRight := e.intValues(rc, []int{7, 8})
	nearMiss := e.f.ConstructLeftJoin(left, nonemptyRight, e.filters(), &memo.JoinPrivate{})
	e.expectNoMatch(rule, nearMiss)
}
