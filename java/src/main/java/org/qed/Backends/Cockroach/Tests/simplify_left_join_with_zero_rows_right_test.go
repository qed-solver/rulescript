package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestSimplifyLeftJoinWithZeroRowsRight(t *testing.T) {
	rule := loadRule(t, "SimplifyLeftJoinWithZeroRowsRight")
	e := newEnv(t)
	left, lc := e.scan("u", 2)
	rcInt := e.f.Metadata().AddColumn("empty_int", types.Int)
	rcBool := e.f.Metadata().AddColumn("empty_bool", types.Bool)
	right := e.empty(rcInt, rcBool)
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rcInt)))
	before := e.f.ConstructLeftJoin(left, right, on, &memo.JoinPrivate{})
	want := e.f.ConstructProject(left, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructNull(types.Int), rcInt),
		e.f.ConstructProjectionsItem(e.f.ConstructNull(types.Bool), rcBool),
	}, opt.MakeColSet(lc...))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(left, right, on, &memo.JoinPrivate{}))
	other, oc := e.scan("t", 2)
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(left, other,
		e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(oc[0]))), &memo.JoinPrivate{}))
}
