package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestUnionToDistinct(t *testing.T) {
	rule := loadRule(t, "UnionToDistinct")
	e := newEnv(t)
	l, lc := e.scan("t", 1)
	r, rc := e.scan("t", 1)
	outCols := opt.ColList{
		e.f.Metadata().AddColumn("o0", types.Int),
	}
	private := &memo.SetPrivate{
		LeftCols:  opt.ColList{lc[0]},
		RightCols: opt.ColList{rc[0]},
		OutCols:   outCols,
	}

	before := e.f.ConstructUnion(l, r, private)
	unionAll := e.f.ConstructUnionAll(l, r, private)
	want := e.f.ConstructGroupBy(unionAll, nil, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(outCols[0]),
	})
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, unionAll)
	e.expectNoMatch(rule, e.f.ConstructIntersect(l, r, private))
	e.expectNoMatch(rule, e.f.ConstructGroupBy(before, nil, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(outCols[0]),
	}))
	e.expectNoMatch(rule, l)
}
