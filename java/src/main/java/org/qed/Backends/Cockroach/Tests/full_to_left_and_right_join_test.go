package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFullToLeftAndRightJoin(t *testing.T) {
	rule := loadRule(t, "FullToLeftAndRightJoin")
	e := newEnv(t)
	l, lc := e.scan("t", 1)
	r, rc := e.scan("u", 1)
	eq := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))
	on := e.filters(eq)
	before := e.f.ConstructFullJoin(l, r, on, &memo.JoinPrivate{})

	outCols := opt.ColList{
		e.f.Metadata().AddColumn("o0", types.Int),
		e.f.Metadata().AddColumn("o1", types.Int),
	}
	base := e.f.Metadata().MaxColumn()
	fresh := opt.ColList{base + 1, base + 2}

	leftJoin := e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{})
	rightJoin := e.f.ConstructRightJoin(l, r, on, &memo.JoinPrivate{})
	notMatched := e.f.ConstructSelect(rightJoin, e.filters(e.f.ConstructIsNot(eq, e.f.ConstructTrue())))
	renamed := e.f.ConstructProject(notMatched, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(lc[0]), fresh[0]),
		e.f.ConstructProjectionsItem(e.v(rc[0]), fresh[1]),
	}, opt.ColSet{})
	want := e.f.ConstructUnionAll(leftJoin, renamed, &memo.SetPrivate{
		LeftCols: opt.ColList{lc[0], rc[0]}, RightCols: fresh, OutCols: outCols,
	})
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructFullJoin(l, r,
		e.filters(e.f.ConstructNe(e.v(lc[0]), e.v(rc[0]))), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructFullJoin(l, r, e.filters(e.f.ConstructTrue()), &memo.JoinPrivate{}))
}
