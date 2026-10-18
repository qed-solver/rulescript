package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFilterSetOpTranspose(t *testing.T) {
	rule := loadRule(t, "FilterSetOpTranspose")
	e := newEnv(t)
	left, lc := e.scan("t", 1)
	right, rc := e.scan("u", 1)
	out := e.f.Metadata().AddColumn("union", types.Int)
	setPrivate := func() *memo.SetPrivate {
		return &memo.SetPrivate{
			LeftCols:  opt.ColList{lc[0]},
			RightCols: opt.ColList{rc[0]},
			OutCols:   opt.ColList{out},
		}
	}
	cond := e.f.ConstructGt(e.v(out), e.num(1))
	before := e.f.ConstructSelect(e.f.ConstructUnionAll(left, right, setPrivate()), e.filters(cond))
	want := e.f.ConstructUnionAll(
		e.f.ConstructSelect(left, e.filters(e.f.ConstructGt(e.v(lc[0]), e.num(1)))),
		e.f.ConstructSelect(right, e.filters(e.f.ConstructGt(e.v(rc[0]), e.num(1)))),
		setPrivate(),
	)
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(e.f.ConstructUnion(left, right, setPrivate()), e.filters(cond)))
	e.expectNoMatch(rule, e.f.ConstructSelect(e.f.ConstructIntersectAll(left, right, setPrivate()), e.filters(cond)))
}
