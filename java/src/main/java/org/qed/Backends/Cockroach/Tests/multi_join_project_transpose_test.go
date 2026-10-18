package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestMultiJoinProjectTranspose(t *testing.T) {
	rule := loadRule(t, "MultiJoinProjectTranspose")
	e := newEnv(t)
	left, lc := e.scan("u", 2)
	right, rc := e.scan("nn", 2)
	computed := e.f.Metadata().AddColumn("computed", types.Int)
	projected := e.f.ConstructProject(left,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(lc[1]), computed)},
		opt.ColSet{})
	before := e.f.ConstructInnerJoin(projected, right,
		e.filters(e.f.ConstructEq(e.v(computed), e.v(rc[0])), e.f.ConstructGt(e.v(rc[1]), e.num(0))),
		&memo.JoinPrivate{})

	joinAfter := e.f.ConstructInnerJoin(left, right,
		e.filters(e.f.ConstructEq(e.v(lc[1]), e.v(rc[0])), e.f.ConstructGt(e.v(rc[1]), e.num(0))),
		&memo.JoinPrivate{})
	want := e.f.ConstructProject(joinAfter,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(lc[1]), computed)},
		opt.MakeColSet(rc[0], rc[1]))
	e.expectRewrite(rule, before, want)

	wrongTypeJoin := e.f.ConstructLeftJoin(projected, right,
		e.filters(e.f.ConstructEq(e.v(computed), e.v(rc[0]))), &memo.JoinPrivate{})
	e.expectNoMatch(rule, wrongTypeJoin)
}
